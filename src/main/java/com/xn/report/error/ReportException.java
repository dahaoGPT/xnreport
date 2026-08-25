package com.xn.report.error;

import java.util.Objects;

/**
 * 报表组件统一根运行时异常。
 * <p>
 * 报表组件内部所有业务校验、配置错误、SQL执行错误及文档渲染失败等场景均抛出或包装为本异常。
 * 内部封装了强类型的 {@link ReportErrorDetail}，便于上层快速诊断和追踪失败上下文。
 * </p>
 */
public class ReportException extends RuntimeException {

    /** 结构化的错误明细信息。 */
    private final ReportErrorDetail detail;

    /**
     * 根据错误码和消息构造异常。
     *
     * @param errorCode 错误码
     * @param message 错误描述
     */
    public ReportException(ReportErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * 根据错误码、消息和原始原因构造异常。
     *
     * @param errorCode 错误码
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ReportException(
            ReportErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, null, null, null, cause);
    }

    /**
     * 根据全量上下文信息构造异常。
     *
     * @param errorCode 错误码
     * @param message 错误描述
     * @param executionId 执行追踪ID
     * @param stage 失败阶段
     * @param reportCode 报表编码
     * @param componentId 组件标识
     * @param cause 原始异常
     */
    public ReportException(
            ReportErrorCode errorCode,
            String message,
            String executionId,
            String stage,
            String reportCode,
            String componentId,
            Throwable cause) {
        this(new ReportErrorDetail(
                errorCode,
                executionId,
                stage,
                reportCode,
                componentId,
                message), cause);
    }

    /**
     * 根据预先构建的错误明细构造异常。
     *
     * @param detail 错误明细对象
     */
    public ReportException(ReportErrorDetail detail) {
        this(detail, null);
    }

    /**
     * 根据错误明细与底层异常原因构造异常。
     *
     * @param detail 错误明细对象，不可为 null
     * @param cause 原始底层异常
     */
    public ReportException(ReportErrorDetail detail, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail").getMessage(), cause);
        this.detail = detail;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码枚举
     */
    public ReportErrorCode getErrorCode() {
        return detail.getErrorCode();
    }

    /**
     * 获取执行追踪ID。
     *
     * @return 执行追踪ID
     */
    public String getExecutionId() {
        return detail.getExecutionId();
    }

    /**
     * 获取执行阶段。
     *
     * @return 阶段名称
     */
    public String getStage() {
        return detail.getStage();
    }

    /**
     * 获取报表编码。
     *
     * @return 报表编码
     */
    public String getReportCode() {
        return detail.getReportCode();
    }

    /**
     * 获取组件标识。
     *
     * @return 组件标识
     */
    public String getComponentId() {
        return detail.getComponentId();
    }

    /**
     * 获取完整的错误明细对象。
     *
     * @return 错误明细对象
     */
    public ReportErrorDetail getDetail() {
        return detail;
    }
}
