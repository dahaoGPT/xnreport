package com.xn.report.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReadOnlySqlGuard {

    private static final Set<String> DANGEROUS_TOKENS =
            new HashSet<String>(Arrays.asList(
                    "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE",
                    "CALL", "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME",
                    "GRANT", "REVOKE", "LOAD", "LOAD_FILE", "LOCK", "UNLOCK",
                    "SET", "USE", "HANDLER", "ANALYZE", "OPTIMIZE", "REPAIR"));

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BACKTICK,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    public void validate(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }

        List<String> tokens = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        State state = State.NORMAL;
        boolean terminalSemicolonSeen = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (state == State.LINE_COMMENT) {
                if (current == '\r' || current == '\n') {
                    state = State.NORMAL;
                }
                continue;
            }
            if (state == State.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    state = State.NORMAL;
                    index++;
                }
                continue;
            }
            if (state == State.SINGLE_QUOTE
                    || state == State.DOUBLE_QUOTE
                    || state == State.BACKTICK) {
                char delimiter = delimiter(state);
                if (state != State.BACKTICK
                        && current == '\\'
                        && index + 1 < sql.length()) {
                    index++;
                } else if (current == delimiter) {
                    if (next == delimiter) {
                        index++;
                    } else {
                        state = State.NORMAL;
                    }
                }
                continue;
            }

            if (Character.isWhitespace(current)) {
                addToken(tokens, token);
                continue;
            }
            if (current == '-' && next == '-'
                    && isMysqlDashCommentStart(sql, index)) {
                addToken(tokens, token);
                state = State.LINE_COMMENT;
                index++;
                continue;
            }
            if (current == '#') {
                addToken(tokens, token);
                state = State.LINE_COMMENT;
                continue;
            }
            if (current == '/' && next == '*') {
                addToken(tokens, token);
                if (index + 2 < sql.length() && sql.charAt(index + 2) == '!') {
                    throw new IllegalArgumentException(
                            "MySQL executable comments are not allowed");
                }
                state = State.BLOCK_COMMENT;
                index++;
                continue;
            }
            if (current == ';') {
                addToken(tokens, token);
                if (terminalSemicolonSeen) {
                    throw new IllegalArgumentException(
                            "Only one terminal SQL semicolon is allowed");
                }
                terminalSemicolonSeen = true;
                continue;
            }
            if (terminalSemicolonSeen) {
                throw new IllegalArgumentException(
                        "SQL must contain exactly one SELECT statement");
            }
            if (current == '\'') {
                addToken(tokens, token);
                state = State.SINGLE_QUOTE;
                continue;
            }
            if (current == '"') {
                addToken(tokens, token);
                state = State.DOUBLE_QUOTE;
                continue;
            }
            if (current == '`') {
                addToken(tokens, token);
                state = State.BACKTICK;
                continue;
            }
            if (isTokenCharacter(current)) {
                token.append(current);
            } else {
                addToken(tokens, token);
            }
        }

        if (state == State.SINGLE_QUOTE
                || state == State.DOUBLE_QUOTE
                || state == State.BACKTICK
                || state == State.BLOCK_COMMENT) {
            throw new IllegalArgumentException("SQL contains an unterminated literal or comment");
        }
        addToken(tokens, token);
        validateTokens(tokens);
    }

    private static void validateTokens(List<String> tokens) {
        if (tokens.isEmpty() || !"SELECT".equals(tokens.get(0))) {
            throw new IllegalArgumentException(
                    "Only a SELECT statement is supported for MySQL 5.7");
        }
        for (String token : tokens) {
            if (DANGEROUS_TOKENS.contains(token)) {
                throw new IllegalArgumentException(
                        "Unsafe SQL keyword is not allowed: " + token);
            }
        }
        for (int index = 0; index + 1 < tokens.size(); index++) {
            String first = tokens.get(index);
            String second = tokens.get(index + 1);
            if ("FOR".equals(first) && "UPDATE".equals(second)) {
                throw new IllegalArgumentException("SELECT FOR UPDATE is not allowed");
            }
            if ("INTO".equals(first)
                    && ("OUTFILE".equals(second) || "DUMPFILE".equals(second))) {
                throw new IllegalArgumentException(
                        "SELECT INTO " + second + " is not allowed");
            }
        }
    }

    private static char delimiter(State state) {
        if (state == State.SINGLE_QUOTE) {
            return '\'';
        }
        if (state == State.DOUBLE_QUOTE) {
            return '"';
        }
        return '`';
    }

    private static boolean isMysqlDashCommentStart(String sql, int index) {
        int following = index + 2;
        return following >= sql.length()
                || Character.isWhitespace(sql.charAt(following))
                || Character.isISOControl(sql.charAt(following));
    }

    private static boolean isTokenCharacter(char character) {
        return Character.isLetterOrDigit(character)
                || character == '_'
                || character == '$';
    }

    private static void addToken(List<String> tokens, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        tokens.add(token.toString().toUpperCase(Locale.ROOT));
        token.setLength(0);
    }
}
