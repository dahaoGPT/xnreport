package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分布分析智能叙述句受控分析器实现。
 * <p>
 * 从指定数据集中提取行记录，调用底层 {@link DistributionAnalyzer} 执行分箱计算，
 * 并将分箱统计指标展开为格式化的 {@code summary} 变量字典（如 {@code {binId}.count}, {@code {binId}.percent}, {@code total} 等）。
 * </p>
 */
final class DistributionNarrativeAnalyzer
        implements ControlledNarrativeAnalyzer {

    private final DistributionAnalyzer analyzer;

    DistributionNarrativeAnalyzer(DistributionAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public NarrativeDefinition.AnalyzerType type() {
        return NarrativeDefinition.AnalyzerType.DISTRIBUTION;
    }

    @Override
    public NarrativeAnalysis analyze(
            NarrativeDefinition narrative, TextRenderContext context) {
        if (!context.datasets().contains(narrative.getDataset())) {
            throw new IllegalArgumentException(
                    "Missing dataset: " + narrative.getDataset());
        }
        DatasetResult dataset =
                context.datasets().get(narrative.getDataset());
        List<DatasetRow> rows = rows(dataset);
        DistributionResult result = analyzer.analyze(
                rows,
                narrative.getDistribution(),
                narrative.getEmptyStrategy());
        if (result.empty()) {
            return new NarrativeAnalysis(
                java.util.Collections.<String, Object>emptyMap(),
                    true,
                    result.skipped(),
                    result.message(),
                    result);
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("total", result.total());
        for (DistributionResult.BinResult bin : result.bins()) {
            summary.put(bin.id() + ".label", bin.label());
            summary.put(bin.id() + ".count", bin.count());
            summary.put(bin.id() + ".percent", bin.percent());
            summary.put(bin.id() + ".displayLabel", bin.displayLabel());
        }
        return new NarrativeAnalysis(summary, false, false, "", result);
    }

    private static List<DatasetRow> rows(DatasetResult dataset) {
        if (dataset.type() == DatasetType.LIST) {
            return dataset.list();
        }
        if (dataset.type() == DatasetType.SINGLE) {
            List<DatasetRow> rows = new ArrayList<DatasetRow>();
            if (dataset.single() != null) {
                rows.add(dataset.single());
            }
            return rows;
        }
        throw new IllegalArgumentException(
                "Distribution analyzer requires LIST or SINGLE dataset: "
                        + dataset.id());
    }
}
