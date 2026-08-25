package com.xn.report.text;

import com.xn.report.config.definition.NarrativeDefinition;

/**
 * 结构化受控文本分析器顶层契约接口。
 * <p>
 * 为特定类型的智能分析（如趋势分析 TREND、分布分析 DISTRIBUTION）提供统一分析驱动能力。
 * </p>
 */
public interface ControlledNarrativeAnalyzer {

    /**
     * 获取分析器支持的分析类型。
     *
     * @return AnalyzerType 枚举
     */
    NarrativeDefinition.AnalyzerType type();

    /**
     * 执行受控分析并产出分析度量摘要。
     *
     * @param definition 叙述句配置定义
     * @param context 文本渲染上下文
     * @return 结构化分析结果 NarrativeAnalysis
     */
    NarrativeAnalysis analyze(
            NarrativeDefinition definition, TextRenderContext context);
}
