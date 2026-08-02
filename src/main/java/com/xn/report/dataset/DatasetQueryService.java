package com.xn.report.dataset;

import com.xn.report.config.ReportDefinition;
import java.util.Map;

public interface DatasetQueryService {

    DatasetContext executeAll(
            ReportDefinition definition, Map<String, Object> runtimeParameters);

    default QueryOutcome executeAllWithWarnings(
            ReportDefinition definition, Map<String, Object> runtimeParameters) {
        return new QueryOutcome(executeAll(definition, runtimeParameters),
                java.util.Collections.<com.xn.report.policy.ReportWarning>emptyList());
    }
}
