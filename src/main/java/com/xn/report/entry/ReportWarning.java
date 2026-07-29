package com.xn.report.entry;

import java.util.Objects;

public final class ReportWarning {

    private final String action;
    private final String scopeType;
    private final String scopeId;
    private final String message;

    public ReportWarning(
            String action, String scopeType, String scopeId, String message) {
        this.action = Objects.requireNonNull(action, "action");
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.message = Objects.requireNonNull(message, "message");
    }

    public static ReportWarning fromPolicy(
            com.xn.report.policy.ReportWarning warning) {
        Objects.requireNonNull(warning, "warning");
        return new ReportWarning(
                warning.getAction(),
                warning.getScopeType(),
                warning.getScopeId(),
                warning.getMessage());
    }

    public static ReportWarning publication(String message) {
        return new ReportWarning("PUBLICATION_CLEANUP", "output", null, message);
    }

    public String getAction() {
        return action;
    }

    public String getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getMessage() {
        return message;
    }
}
