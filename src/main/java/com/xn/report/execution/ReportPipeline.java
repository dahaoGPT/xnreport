package com.xn.report.execution;

import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportExecutionResult;

public interface ReportPipeline {

    ReportExecutionResult execute(ReportExecutionRequest request);
}
