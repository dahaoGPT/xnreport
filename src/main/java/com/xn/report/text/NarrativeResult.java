package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;
import java.util.Collections;
import java.util.Map;

/**
 * 智能叙述句生成结果数据模型。
 * <p>
 * 包含生成的最终结论文案（text）、分析变量摘要（summaryValues）、源类型（sourceType）及底层领域分析产物（analysisResult）。
 * </p>
 */
public final class NarrativeResult {

    /** 最终生成的文案文本。 */
    private final String text;

    /** 叙述句源类型。 */
    private final NarrativeDefinition.SourceType sourceType;

    /** 目标数据集 ID。 */
    private final String datasetId;

    /** 分析器标识。 */
    private final String analyzerId;

    /** 分析展开的度量摘要字典。 */
    private final Map<String, Object> summaryValues;

    /** 是否被空数据策略跳过。 */
    private final boolean skipped;

    /** 原始分析算法领域结果对象。 */
    private final Object analysisResult;

    NarrativeResult(
            String text,
            NarrativeDefinition.SourceType sourceType,
            String datasetId,
            String analyzerId,
            Map<String, Object> summaryValues,
            boolean skipped,
            Object analysisResult) {
        this.text = text == null ? "" : text;
        this.sourceType = sourceType;
        this.datasetId = datasetId;
        this.analyzerId = analyzerId;
        this.summaryValues = TextValueSnapshot.map(summaryValues);
        this.skipped = skipped;
        this.analysisResult = analysisResult;
    }

    public String text() {
        return text;
    }

    public NarrativeDefinition.SourceType sourceType() {
        return sourceType;
    }

    public String datasetId() {
        return datasetId;
    }

    public String analyzerId() {
        return analyzerId;
    }

    public Map<String, Object> summaryValues() {
        return summaryValues;
    }

    public boolean skipped() {
        return skipped;
    }

    public Object analysisResult() {
        return analysisResult;
    }
}
