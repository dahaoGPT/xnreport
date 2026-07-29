package com.xn.report.policy;

import com.xn.report.config.definition.PolicyDefinition;
import java.util.Objects;

/**
 * Resolves a scoped policy and records the warning caused by applying it in one
 * call. Execution stages use this bridge instead of separately resolving a
 * policy and remembering to emit a warning.
 */
public final class PolicyExecutionBridge {

    private final PolicyResolver resolver;

    public PolicyExecutionBridge(PolicyResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public EmptyDataPolicy onEmptyData(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report,
            String scopeType,
            String scopeId,
            String message) {
        EmptyDataPolicy policy = resolver.resolveEmptyData(
                component, rule, dataset, report);
        resolver.recordApplied(policy, scopeType, scopeId, message);
        return policy;
    }

    public MissingFieldPolicy onMissingField(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report,
            String scopeType,
            String scopeId,
            String message) {
        MissingFieldPolicy policy = resolver.resolveMissingField(
                component, rule, dataset, report);
        resolver.recordApplied(policy, scopeType, scopeId, message);
        return policy;
    }

    public TypeMismatchPolicy onTypeMismatch(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report,
            String scopeType,
            String scopeId,
            String message) {
        TypeMismatchPolicy policy = resolver.resolveTypeMismatch(
                component, rule, dataset, report);
        resolver.recordApplied(policy, scopeType, scopeId, message);
        return policy;
    }

    public NullValuePolicy onNullValue(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report,
            String scopeType,
            String scopeId,
            String message) {
        NullValuePolicy policy = resolver.resolveNullValue(
                component, rule, dataset, report);
        resolver.recordApplied(policy, scopeType, scopeId, message);
        return policy;
    }
}
