package com.xn.report.text;

import java.util.Map;

/**
 * 叙述句受控分析阶段产物数据包。
 * <p>
 * 封装分析算法产出的变量摘要字典（summary）、是否为空/跳过状态以及原始分析结果领域对象。
 * </p>
 */
public final class NarrativeAnalysis {

    /** 展开的分析变量摘要字典。 */
    private final Map<String, Object> summary;

    /** 是否为空数据。 */
    private final boolean empty;

    /** 是否跳过该叙述句生成。 */
    private final boolean skipped;

    /** 降级或提示消息。 */
    private final String message;

    /** 原始分析算法领域结果对象（如 TrendResult, DistributionResult）。 */
    private final Object analysisResult;

    public NarrativeAnalysis(
            Map<String, Object> summary,
            boolean empty,
            boolean skipped,
            String message,
            Object analysisResult) {
        this.summary = TextValueSnapshot.map(summary);
        this.empty = empty;
        this.skipped = skipped;
        this.message = message == null ? "" : message;
        this.analysisResult = analysisResult;
    }

    public Map<String, Object> summary() {
        return summary;
    }

    public boolean empty() {
        return empty;
    }

    public boolean skipped() {
        return skipped;
    }

    public String message() {
        return message;
    }

    public Object analysisResult() {
        return analysisResult;
    }
}
