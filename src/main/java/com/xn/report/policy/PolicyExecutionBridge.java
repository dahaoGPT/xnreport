package com.xn.report.policy;

import com.xn.report.config.definition.PolicyDefinition;
import java.util.Objects;

/**
 * 策略执行桥接器。
 * <p>
 * 将多级策略解析与警告自动记录合并为原子操作。
 * 各渲染与执行阶段直接调用本桥接器，在解析策略的同时自动完成非致命策略动作的警告事件发射，避免遗漏记录。
 * </p>
 */
public final class PolicyExecutionBridge {

    /** 策略解析器。 */
    private final PolicyResolver resolver;

    /**
     * 构造策略执行桥接器。
     *
     * @param resolver 策略解析器，不可为 null
     */
    public PolicyExecutionBridge(PolicyResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * 解析空数据策略并自动记录警告。
     *
     * @param component 组件级策略
     * @param rule 规则级策略
     * @param dataset 数据集级策略
     * @param report 报表级策略
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param message 警告说明
     * @return 解析后的空数据策略
     */
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

    /**
     * 解析缺失字段策略并自动记录警告。
     *
     * @param component 组件级策略
     * @param rule 规则级策略
     * @param dataset 数据集级策略
     * @param report 报表级策略
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param message 警告说明
     * @return 解析后的缺失字段策略
     */
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

    /**
     * 解析类型不匹配策略并自动记录警告。
     *
     * @param component 组件级策略
     * @param rule 规则级策略
     * @param dataset 数据集级策略
     * @param report 报表级策略
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param message 警告说明
     * @return 解析后的类型不匹配策略
     */
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

    /**
     * 解析空值策略并自动记录警告。
     *
     * @param component 组件级策略
     * @param rule 规则级策略
     * @param dataset 数据集级策略
     * @param report 报表级策略
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param message 警告说明
     * @return 解析后的空值策略
     */
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
