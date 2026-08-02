package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.text.NarrativeResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public final class WordRenderContext {

    private final DatasetContext datasets;
    private final Map<String, NarrativeResult> narratives;
    private final Map<String, RenderedChart> charts;

    private WordRenderContext(Builder builder) {
        this.datasets = builder.datasets == null
                ? DatasetContext.builder().build() : builder.datasets;
        this.narratives = Collections.unmodifiableMap(
                new LinkedHashMap<String, NarrativeResult>(builder.narratives));
        this.charts = Collections.unmodifiableMap(
                new LinkedHashMap<String, RenderedChart>(builder.charts));
    }

    public static Builder builder() {
        return new Builder();
    }

    public DatasetContext datasets() {
        return datasets;
    }

    public NarrativeResult narrative(String id) {
        return narratives.get(id);
    }

    public RenderedChart chart(String id) {
        return charts.get(id);
    }

    /** Returns the base chart and every grouped chart in insertion order. */
    public List<RenderedChart> charts(String baseId) {
        List<RenderedChart> matches = new ArrayList<RenderedChart>();
        String groupedPrefix = baseId + "::";
        for (Map.Entry<String, RenderedChart> entry : charts.entrySet()) {
            if (entry.getKey().equals(baseId)
                    || entry.getKey().startsWith(groupedPrefix)) {
                matches.add(entry.getValue());
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public static final class Builder {
        private DatasetContext datasets;
        private final Map<String, NarrativeResult> narratives =
                new LinkedHashMap<String, NarrativeResult>();
        private final Map<String, RenderedChart> charts =
                new LinkedHashMap<String, RenderedChart>();

        public Builder datasets(DatasetContext datasets) {
            this.datasets = datasets;
            return this;
        }

        public Builder narrative(String id, NarrativeResult result) {
            require(id, result, "narrative");
            narratives.put(id, result);
            return this;
        }

        public Builder chart(String id, RenderedChart result) {
            require(id, result, "chart");
            charts.put(id, result);
            return this;
        }

        public WordRenderContext build() {
            return new WordRenderContext(this);
        }

        private static void require(String id, Object value, String kind) {
            if (id == null || id.trim().isEmpty() || value == null) {
                throw new IllegalArgumentException(
                        "Word " + kind + " id and value are required");
            }
        }
    }
}
