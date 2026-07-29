package com.xn.report.policy;

import com.xn.report.config.definition.PolicyDefinition;
import java.util.Objects;

public final class PolicyResolver {

    private final PolicyDefinition systemDefaults;
    private final WarningSink warningSink;

    public PolicyResolver(PolicyDefinition systemDefaults) {
        this(systemDefaults, WarningSink.ignoring());
    }

    public PolicyResolver(
            PolicyDefinition systemDefaults, WarningSink warningSink) {
        this.systemDefaults =
                Objects.requireNonNull(systemDefaults, "systemDefaults");
        this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
    }

    public EmptyDataPolicy resolveEmptyData(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report) {
        for (PolicyDefinition scope : scopes(
                component, rule, dataset, report, systemDefaults)) {
            if (scope != null && scope.getEmptyData() != null) {
                return scope.getEmptyData();
            }
        }
        return EmptyDataPolicy.OUTPUT_MESSAGE;
    }

    public MissingFieldPolicy resolveMissingField(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report) {
        for (PolicyDefinition scope : scopes(
                component, rule, dataset, report, systemDefaults)) {
            if (scope != null && scope.getMissingField() != null) {
                return scope.getMissingField();
            }
        }
        return MissingFieldPolicy.FAIL;
    }

    public TypeMismatchPolicy resolveTypeMismatch(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report) {
        for (PolicyDefinition scope : scopes(
                component, rule, dataset, report, systemDefaults)) {
            if (scope != null && scope.getTypeMismatch() != null) {
                return scope.getTypeMismatch();
            }
        }
        return TypeMismatchPolicy.FAIL;
    }

    public NullValuePolicy resolveNullValue(
            PolicyDefinition component,
            PolicyDefinition rule,
            PolicyDefinition dataset,
            PolicyDefinition report) {
        for (PolicyDefinition scope : scopes(
                component, rule, dataset, report, systemDefaults)) {
            if (scope != null && scope.getNullValue() != null) {
                return scope.getNullValue();
            }
        }
        return NullValuePolicy.RULE_NOT_MATCHED;
    }

    public void recordApplied(
            Enum<?> policy, String scopeType, String scopeId, String message) {
        Objects.requireNonNull(policy, "policy");
        if (isWarningAction(policy.name())) {
            warningSink.accept(new ReportWarning(
                    policy.name(), scopeType, scopeId, message));
        }
    }

    private static boolean isWarningAction(String action) {
        return "SKIP".equals(action)
                || "WARN_AND_SKIP".equals(action)
                || "USE_DEFAULT".equals(action);
    }

    private static PolicyDefinition[] scopes(PolicyDefinition... scopes) {
        return scopes;
    }
}
