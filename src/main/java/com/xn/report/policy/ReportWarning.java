package com.xn.report.policy;

import java.util.Objects;

/**
 * Pipeline-neutral warning produced when a policy deliberately skips data or
 * substitutes a default. Task orchestration may map this immutable value into
 * its public execution result without coupling the policy layer to the entry API.
 */
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
