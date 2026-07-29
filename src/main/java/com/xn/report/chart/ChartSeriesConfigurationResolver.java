package com.xn.report.chart;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.config.definition.ChartSeriesDefinition;

/**
 * Resolves a rendered series back to its exact configured source series.
 */
public final class ChartSeriesConfigurationResolver {

    private ChartSeriesConfigurationResolver() {
    }

    public static ChartSeriesDefinition resolve(
            ChartDefinition definition,
            ChartSeriesModel model,
            int ordinal) {
        if (definition == null || model == null) {
            throw new IllegalArgumentException(
                    "Chart definition and series model must not be null");
        }
        int index = model.getSourceIndex() >= 0
                ? model.getSourceIndex() : ordinal;
        if (index < 0 || index >= definition.getSeries().size()) {
            throw new IllegalArgumentException(
                    "Chart series source index is invalid: " + index);
        }
        ChartSeriesDefinition configured =
                definition.getSeries().get(index);
        if (configured.getField() == null
                || !configured.getField().equalsIgnoreCase(
                        model.getField())
                || configured.getType() != model.getType()) {
            throw new IllegalArgumentException(
                    "Chart series source identity mismatch at index "
                            + index + ": " + model.getField()
                            + "/" + model.getType());
        }
        return configured;
    }
}
