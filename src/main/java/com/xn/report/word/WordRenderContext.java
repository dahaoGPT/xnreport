package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.text.NarrativeResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档渲染上下文环境模型。
 * <p>
 * 聚合为 Word 生成提供数据支持的所有运行时上下文要素：
 * <ul>
 *   <li>数据集结果集集合（{@link DatasetContext}）。</li>
 *   <li>叙述与规则分析结果集合（{@link NarrativeResult}）。</li>
 *   <li>已渲染的离线图表 PNG 产物（{@link RenderedChart}），支持按基础图表 ID 获取分组展开后的所有子图表序列。</li>
 * </ul>
 * </p>
 */
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

    /**
     * 按插入顺序返回基础图表及所有分组派生子图表列表。
     *
     * @param baseId 基础图表 ID
     * @return 匹配的图表图像列表
     */
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

    /**
     * 上下文建造者。
     */
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
