package com.xn.report.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 只读 SQL 语法安全防护器。
 * <p>
 * 对待执行的 SQL 脚本执行严格的词法分析与安全防护校验，确保只能执行纯只读的 SELECT 语句：
 * <ul>
 *   <li><b>多模式词法分析</b>：在 MySQL 5.7 的 4 种词法模式（ANSI_QUOTES、NO_BACKSLASH_ESCAPES 等）下全面解析字符串字面量、反引号与注释。</li>
 *   <li><b>危险操作拦截</b>：拦截 INSERT, UPDATE, DELETE, REPLACE, MERGE, CALL, CREATE, ALTER, DROP, TRUNCATE, GRANT, LOCK, SET, LOAD 等修改/管理操作。</li>
 *   <li><b>高级注入与逃逸防御</b>：拦截 {@code SELECT FOR UPDATE} 行锁语句、{@code SELECT INTO OUTFILE/DUMPFILE} 文件导出语句、MySQL 可执行注释（如 /*! ... *&#47; 语法）以及多语句（分号注入）。</li>
 * </ul>
 * </p>
 */
public final class ReadOnlySqlGuard {

    /** 禁止出现的危险 SQL 关键字集合。 */
    private static final Set<String> DANGEROUS_TOKENS =
            new HashSet<String>(Arrays.asList(
                    "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE",
                    "CALL", "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME",
                    "GRANT", "REVOKE", "LOAD", "LOAD_FILE", "LOCK", "UNLOCK",
                    "SET", "USE", "HANDLER", "ANALYZE", "OPTIMIZE", "REPAIR"));

    /** 词法解析状态机状态。 */
    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BACKTICK,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    /** MySQL 词法分析模式（考虑反斜杠转义与 ANSI 双引号开关）。 */
    private enum LexerMode {
        DEFAULT(true, true),
        NO_BACKSLASH_ESCAPES(false, false),
        ANSI_QUOTES(true, false),
        ANSI_QUOTES_NO_BACKSLASH_ESCAPES(false, false);

        private final boolean singleQuoteBackslashEscapes;
        private final boolean doubleQuoteBackslashEscapes;

        LexerMode(
                boolean singleQuoteBackslashEscapes,
                boolean doubleQuoteBackslashEscapes) {
            this.singleQuoteBackslashEscapes = singleQuoteBackslashEscapes;
            this.doubleQuoteBackslashEscapes = doubleQuoteBackslashEscapes;
        }

        private boolean backslashEscapes(State state) {
            if (state == State.SINGLE_QUOTE) {
                return singleQuoteBackslashEscapes;
            }
            if (state == State.DOUBLE_QUOTE) {
                return doubleQuoteBackslashEscapes;
            }
            return false;
        }
    }

    /**
     * 校验 SQL 是否为合法且纯粹的只读单条 SELECT 语句。
     *
     * @param sql 待校验的 SQL 文本
     * @throws IllegalArgumentException 如果 SQL 包含危险关键字、包含多语句或语法不合法
     */
    public void validate(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }
        // 在 MySQL 所有可能开启的词法模式下分别进行解析校验，确保无逃逸漏洞
        for (LexerMode mode : LexerMode.values()) {
            try {
                validate(sql, mode);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "SQL is unsafe under MySQL lexer mode "
                                + mode + ": " + exception.getMessage(),
                        exception);
            }
        }
    }

    /**
     * 在指定的词法模式下执行有限状态机词法分析。
     */
    private void validate(String sql, LexerMode mode) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        State state = State.NORMAL;
        boolean terminalSemicolonSeen = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            // 处理单行注释 (-- 或 #)
            if (state == State.LINE_COMMENT) {
                if (current == '\r' || current == '\n') {
                    state = State.NORMAL;
                }
                continue;
            }
            // 处理块级注释 (/* ... */)
            if (state == State.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    state = State.NORMAL;
                    index++;
                }
                continue;
            }
            // 处理引号和反引号字面量
            if (state == State.SINGLE_QUOTE
                    || state == State.DOUBLE_QUOTE
                    || state == State.BACKTICK) {
                char delimiter = delimiter(state);
                if (mode.backslashEscapes(state)
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
                // 禁止 MySQL 条件执行注释 /*! ... */
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

    /**
     * 校验提取出的 Token 列表是否满足只读安全规范。
     */
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
