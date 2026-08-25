package com.xn.report.config;

import java.util.Objects;

/**
 * 配置静态校验问题项。
 * <p>
 * 封装在配置文件 Schema 校验、引用关系校验、循环依赖检测中发现的单条校验错误或警告，
 * 包含错误码（code）、JSON/YAML 路径表达式（path，如 {@code $.datasets[0].sql}）及错误描述（message）。
 * 本对象为不可变对象。
 * </p>
 */
public final class ValidationIssue {

    /** 校验错误码（如 "CFG-SCHEMA-VERSION"、"CHART-001" 等）。 */
    private final String code;

    /** 发生问题的配置路径（JSONPath 格式，如 "$.report.code"）。 */
    private final String path;

    /** 详细错误描述信息。 */
    private final String message;

    /**
     * 构造校验问题项。
     *
     * @param code 错误码，不可为 null
     * @param path 配置路径（为 null 时自动转为空字符串）
     * @param message 错误描述信息，不可为 null
     */
    public ValidationIssue(String code, String path, String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.path = path == null ? "" : path;
        this.message = Objects.requireNonNull(message, "message");
    }

    public String getCode() {
        return code;
    }

    public String getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ValidationIssue)) {
            return false;
        }
        ValidationIssue other = (ValidationIssue) object;
        return code.equals(other.code)
                && path.equals(other.path)
                && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, path, message);
    }

    @Override
    public String toString() {
        return code + (path.isEmpty() ? "" : " at " + path) + ": " + message;
    }
}
