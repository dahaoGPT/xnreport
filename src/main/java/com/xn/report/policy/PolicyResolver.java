package com.xn.report.policy;

import com.xn.report.config.definition.PolicyDefinition;
import java.util.Objects;

/**
 * 策略解析器。
 * <p>
 * 实现策略的多层作用域就近覆盖查找机制：
 * 优先级顺序为：{@code component (组件级)} &gt; {@code rule (规则级)} &gt; {@code dataset (数据集级)} &gt; {@code report (报表级)} &gt; {@code systemDefaults (系统默认)}。
 * 并负责在触发非终止性降级操作时向 {@link WarningSink} 记录警告事件。
 * </p>
 */
public final class PolicyResolver {

    /** 系统级全局兜底默认策略。 */
    private final PolicyDefinition systemDefaults;

    /** 警告接收器。 */
    private final WarningSink warningSink;

    /**
     * 使用系统默认策略构造解析器（默认忽略警告）。
     *
     * @param systemDefaults 系统兜底策略配置，不可为 null
     */
    public PolicyResolver(PolicyDefinition systemDefaults) {
        this(systemDefaults, WarningSink.ignoring());
    }

    /**
     * 使用系统默认策略与指定的警告接收器构造解析器。
     *
     * @param systemDefaults 系统兜底策略配置，不可为 null
     * @param warningSink 警告接收器，不可为 null
     */
    public PolicyResolver(
            PolicyDefinition systemDefaults, WarningSink warningSink) {
        this.systemDefaults =
                Objects.requireNonNull(systemDefaults, "systemDefaults");
        this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
    }

    /**
     * 按照层级就近解析空数据策略。
     *
     * @param component 组件级策略配置
     * @param rule 规则级策略配置
     * @param dataset 数据集级策略配置
     * @param report 报表级策略配置
     * @return 解析得出的空数据策略（默认兜底为 {@link EmptyDataPolicy#OUTPUT_MESSAGE}）
     */
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

    /**
     * 按照层级就近解析缺失字段策略。
     *
     * @param component 组件级策略配置
     * @param rule 规则级策略配置
     * @param dataset 数据集级策略配置
     * @param report 报表级策略配置
     * @return 解析得出的缺失字段策略（默认兜底为 {@link MissingFieldPolicy#FAIL}）
     */
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

    /**
     * 按照层级就近解析类型不匹配策略。
     *
     * @param component 组件级策略配置
     * @param rule 规则级策略配置
     * @param dataset 数据集级策略配置
     * @param report 报表级策略配置
     * @return 解析得出的类型不匹配策略（默认兜底为 {@link TypeMismatchPolicy#FAIL}）
     */
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

    /**
     * 按照层级就近解析空值策略。
     *
     * @param component 组件级策略配置
     * @param rule 规则级策略配置
     * @param dataset 数据集级策略配置
     * @param report 报表级策略配置
     * @return 解析得出的空值策略（默认兜底为 {@link NullValuePolicy#RULE_NOT_MATCHED}）
     */
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

    /**
     * 记录已应用策略产生的警告信息。
     *
     * @param policy 应用的策略枚举
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param message 警告说明信息
     */
    public void recordApplied(
            Enum<?> policy, String scopeType, String scopeId, String message) {
        Objects.requireNonNull(policy, "policy");
        if (isWarningAction(policy.name())) {
            warningSink.accept(new ReportWarning(
                    policy.name(), scopeType, scopeId, message));
        }
    }

    /**
     * 判断策略动作是否属于需要记录警告的非致命动作。
     */
    private static boolean isWarningAction(String action) {
        return "SKIP".equals(action)
                || "WARN_AND_SKIP".equals(action)
                || "USE_DEFAULT".equals(action);
    }

    /**
     * 构造作用域遍历数组。
     */
    private static PolicyDefinition[] scopes(PolicyDefinition... scopes) {
        return scopes;
    }
}
