package com.xn.report.error;

import java.util.Objects;

/**
 * 报表生成失败时的详细错误信息值对象。
 * <p>
 * 封装了错误码、执行追踪ID、失败阶段、报表编码、出错的具体组件标识（如 datasetId、ruleId）以及面向用户的详细错误信息。
 * 本对象为不可变对象，保证线程安全。
 * </p>
 */
public final class ReportErrorDetail {

    /** 错误分类码。 */
    private final ReportErrorCode errorCode;

    /** 报表单次执行的唯一追踪标识（UUID）。 */
    private final String executionId;

    /** 发生错误的流水线执行阶段（如 LOAD_CONFIG、QUERY、GENERATE_WORD 等）。 */
    private final String stage;

    /** 报表唯一业务编码。 */
    private final String reportCode;

    /** 出错的目标组件或配置标识（如具体的数据集名称、规则名称、图表名称等）。 */
    private final String componentId;

    /** 面向用户的详细错误描述。 */
    private final String message;

    /**
     * 构造完整的错误明细对象。
     *
     * @param errorCode 错误分类码，不可为 null
     * @param executionId 执行追踪ID
     * @param stage 失败阶段
     * @param reportCode 报表编码
     * @param componentId 组件标识
     * @param message 错误描述信息，不可为 null
     */
    public ReportErrorDetail(
            ReportErrorCode errorCode,
            String executionId,
            String stage,
            String reportCode,
            String componentId,
            String message) {
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.executionId = executionId;
        this.stage = stage;
        this.reportCode = reportCode;
        this.componentId = componentId;
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * 获取错误码。
     *
     * @return 错误码枚举
     */
    public ReportErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取执行追踪ID。
     *
     * @return 执行追踪ID
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * 获取发生错误的阶段名称。
     *
     * @return 阶段名称
     */
    public String getStage() {
        return stage;
    }

    /**
     * 获取报表编码。
     *
     * @return 报表编码
     */
    public String getReportCode() {
        return reportCode;
    }

    /**
     * 获取出错的组件标识。
     *
     * @return 组件标识
     */
    public String getComponentId() {
        return componentId;
    }

    /**
     * 获取错误描述信息。
     *
     * @return 错误信息
     */
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReportErrorDetail)) {
            return false;
        }
        ReportErrorDetail that = (ReportErrorDetail) other;
        return errorCode == that.errorCode
                && Objects.equals(executionId, that.executionId)
                && Objects.equals(stage, that.stage)
                && Objects.equals(reportCode, that.reportCode)
                && Objects.equals(componentId, that.componentId)
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                errorCode, executionId, stage, reportCode, componentId, message);
    }
}
