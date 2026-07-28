package com.xn.report.rule;

import com.xn.report.dataset.DatasetContext;
import java.util.Map;

public final class RuleEvaluationContext {

    private final DatasetContext datasets;
    private final Map<String, Object> runtimeParameters;

    public RuleEvaluationContext(
            DatasetContext datasets, Map<String, Object> runtimeParameters) {
        if (datasets == null) {
            throw new IllegalArgumentException("Dataset context is required");
        }
        if (runtimeParameters == null) {
            throw new IllegalArgumentException("Runtime parameters are required");
        }
        this.datasets = datasets;
        this.runtimeParameters = RuleValues.freezeMap(runtimeParameters);
    }

    public DatasetContext getDatasets() {
        return datasets;
    }

    public Object getRuntimeParameter(String name) {
        if (!runtimeParameters.containsKey(name)) {
            throw RuleErrors.reference("Missing runtime parameter: " + name);
        }
        return RuleValues.copyValue(runtimeParameters.get(name));
    }

    public Map<String, Object> getRuntimeParameters() {
        return RuleValues.copyMap(runtimeParameters);
    }
}
