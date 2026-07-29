package com.xn.report.execution;

import com.xn.report.config.ReportDefinition;

@FunctionalInterface
public interface ReportConfigValidator {

    void validateOrThrow(ReportDefinition definition);
}
