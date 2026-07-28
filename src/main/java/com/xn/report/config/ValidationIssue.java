package com.xn.report.config;

import java.util.Objects;

public final class ValidationIssue {

    private final String code;
    private final String path;
    private final String message;

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
