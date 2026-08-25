package com.xn.report.entry;

import java.util.Objects;

/**
 * 外部客户端接收的非致命警告通知领域模型。
 * <p>
 * 封装在数据查询、分析清洗、图表生成或发布清理过程中产生的业务告警信息。
 * </p>
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

    /**
     * 将内部策略警告转换为门面警告模型。
     *
     * @param warning 策略警告对象
     * @return 门面警告对象
     */
    public static ReportWarning fromPolicy(
            com.xn.report.policy.ReportWarning warning) {
        Objects.requireNonNull(warning, "warning");
        return new ReportWarning(
                warning.getAction(),
                warning.getScopeType(),
                warning.getScopeId(),
                warning.getMessage());
    }

    /**
     * 构建发布阶段产生的清理告警。
     *
     * @param message 告警信息
     * @return 门面警告对象
     */
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
