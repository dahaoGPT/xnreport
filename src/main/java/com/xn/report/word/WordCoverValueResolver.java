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

/** Resolves runtime expressions in a defensive copy of the Word cover. */
public final class WordCoverValueResolver {

    private final TextRenderer renderer;

    public WordCoverValueResolver() {
        this(TextRenderer.createDefault());
    }

    WordCoverValueResolver(TextRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

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
