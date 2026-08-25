package com.xn.report.word;

import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.config.definition.WordCoverDefinition;
import com.xn.report.text.TextRenderContext;
import com.xn.report.text.TextRenderer;
import com.xn.report.text.UnresolvedPlaceholderPolicy;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Word 封面表达式动态解析求值器。
 * <p>
 * 对封面属性（title、organization、reportPeriod、preparedBy、preparedDate）进行防御性克隆与动态占位符解析（如 <code>${runtime.year}</code>），支持配置未解析占位符策略（FAIL / EMPTY / PRESERVE）。
 * </p>
 */
public final class WordCoverValueResolver {

    private final TextRenderer renderer;

    public WordCoverValueResolver() {
        this(TextRenderer.createDefault());
    }

    WordCoverValueResolver(TextRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * 解析封面配置中的动态表达式。
     *
     * @param source 原始封面定义
     * @param runtime 运行时全局参数
     * @param policies 全局策略定义
     * @return 解析后的新封面定义副本
     */
    public WordCoverDefinition resolve(
            WordCoverDefinition source,
            Map<String, Object> runtime,
            PolicyDefinition policies) {
        Objects.requireNonNull(source, "source");
        TextRenderContext context = TextRenderContext.builder()
                .runtime(runtime == null
                        ? Collections.<String, Object>emptyMap() : runtime)
                .build();
        UnresolvedPlaceholderPolicy unresolved = unresolvedPolicy(policies);
        WordCoverDefinition result = new WordCoverDefinition();
        result.setTitle(resolve("title", source.getTitle(), context, unresolved));
        result.setOrganization(resolve(
                "organization", source.getOrganization(), context, unresolved));
        result.setReportPeriod(resolve(
                "reportPeriod", source.getReportPeriod(), context, unresolved));
        result.setPreparedBy(resolve(
                "preparedBy", source.getPreparedBy(), context, unresolved));
        result.setPreparedDate(resolve(
                "preparedDate", source.getPreparedDate(), context, unresolved));
        return result;
    }

    private String resolve(
            String field,
            String value,
            TextRenderContext context,
            UnresolvedPlaceholderPolicy policy) {
        try {
            return renderer.render(value, context, policy);
        } catch (RuntimeException exception) {
            throw new WordTemplateException(
                    "Unable to resolve Word cover " + field + ": "
                            + exception.getMessage(), exception);
        }
    }

    private static UnresolvedPlaceholderPolicy unresolvedPolicy(
            PolicyDefinition policies) {
        String configured = policies == null
                ? null : policies.getUnresolvedPlaceholder();
        if (configured == null || configured.trim().isEmpty()) {
            return UnresolvedPlaceholderPolicy.FAIL;
        }
        try {
            return UnresolvedPlaceholderPolicy.valueOf(
                    configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new WordTemplateException(
                    "Unknown unresolved placeholder policy: " + configured,
                    exception);
        }
    }
}
