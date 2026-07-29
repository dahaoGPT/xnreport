package com.xn.report.output;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OutputNameRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern WINDOWS_ILLEGAL =
            Pattern.compile("[<>:\"|?*\\p{Cntrl}]");
    private static final Pattern TRAILING_SPACE_OR_DOT =
            Pattern.compile("[ .]+$");
    private static final Pattern WINDOWS_RESERVED =
            Pattern.compile(
                    "(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\.|$)");
    private final int maxLength;

    public OutputNameRenderer() {
        this(180);
    }

    public OutputNameRenderer(int maxLength) {
        if (maxLength < 10) {
            throw new IllegalArgumentException("maxLength must be at least 10");
        }
        this.maxLength = maxLength;
    }

    public String render(String template, Map<String, ?> values) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");
        rejectPathSyntax(template);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw invalid("unresolved output name placeholder: " + key);
            }
            Object value = values.get(key);
            matcher.appendReplacement(
                    rendered,
                    Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(rendered);

        String value = rendered.toString();
        if (value.contains("${")) {
            throw invalid("unresolved output name placeholder remains after rendering");
        }
        rejectPathSyntax(value);
        String extension = extension(value);
        if (!".xlsx".equalsIgnoreCase(extension)
                && !".docx".equalsIgnoreCase(extension)) {
            throw invalid("output file extension must be .xlsx or .docx");
        }
        String base = value.substring(0, value.length() - extension.length());
        base = WINDOWS_ILLEGAL.matcher(base).replaceAll("_");
        base = TRAILING_SPACE_OR_DOT.matcher(base).replaceAll("");
        if (base.isEmpty()) {
            throw invalid("output file name is empty after sanitization");
        }
        if (WINDOWS_RESERVED.matcher(base).find()) {
            base = "_" + base;
        }
        int allowedBaseLength = maxLength - extension.length();
        if (base.length() > allowedBaseLength) {
            base = base.substring(0, allowedBaseLength);
            base = TRAILING_SPACE_OR_DOT.matcher(base).replaceAll("");
        }
        return base + extension.toLowerCase();
    }

    private static void rejectPathSyntax(String value) {
        if (value.contains("..")
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0) {
            throw invalid("output path traversal or directory separator is not allowed");
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static ReportException invalid(String message) {
        return new ReportException(ReportErrorCode.OUT_001, message);
    }
}
