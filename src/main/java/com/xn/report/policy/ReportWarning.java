package com.xn.report.policy;

import java.util.Objects;

/**
 * 策略执行层警告事件值对象。
 * <p>
 * 当策略引擎采取跳过数据、填充默认值或触发降级策略时产生此不可变对象，
 * 用于向上层流水线和最终执行结果传递警告上下文，且不与上层 API 强耦合。
 * </p>
 */
public final class ReportWarning {

    /** 触发警告的具体动作（如 SKIP、USE_DEFAULT、WARN_AND_SKIP）。 */
    private final String action;

    /** 发生警告的作用域类型（如 DATASET、RULE、CHART、TABLE、SECTION、REPORT）。 */
    private final String scopeType;

    /** 发生警告的作用域标识（如具体的 datasetId、ruleId 等）。 */
    private final String scopeId;

    /** 警告详细说明信息。 */
    private final String message;

    /**
     * 构造策略警告事件对象。
     *
     * @param action 采取的动作，不可为 null
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param message 警告说明信息，不可为 null
     */
    public ReportWarning(
            String action, String scopeType, String scopeId, String message) {
        this.action = Objects.requireNonNull(action, "action");
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * 获取触发警告的动作名称。
     *
     * @return 动作名称
     */
    public String getAction() {
        return action;
    }

    /**
     * 获取作用域类型。
     *
     * @return 作用域类型
     */
    public String getScopeType() {
        return scopeType;
    }

    /**
     * 获取作用域标识。
     *
     * @return 作用域标识
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取警告说明消息。
     *
     * @return 警告说明消息
     */
    public String getMessage() {
        return message;
    }
}
