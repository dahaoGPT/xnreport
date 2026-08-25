package com.xn.report.execution;

import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportExecutionResult;

/**
 * 报表生成核心流水线接口。
 */
public interface ReportPipeline {

    /**
     * 驱动整条报表生成流水线执行。
     *
     * @param request 执行请求对象
     * @return 执行结果对象
     */
    ReportExecutionResult execute(ReportExecutionRequest request);
}
