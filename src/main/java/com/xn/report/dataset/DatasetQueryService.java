package com.xn.report.dataset;

import com.xn.report.config.ReportDefinition;
import java.util.Map;

public interface DatasetQueryService {

    DatasetContext executeAll(
            ReportDefinition definition, Map<String, Object> runtimeParameters);
}
