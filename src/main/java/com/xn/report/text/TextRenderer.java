package com.xn.report.text;

import com.xn.report.text.PlaceholderParser.Part;
import java.util.List;

public final class TextRenderer {

    private final PlaceholderParser parser;
    private final FormatterRegistry formatters;

    public TextRenderer(
            PlaceholderParser parser, FormatterRegistry formatters) {
        if (parser == null || formatters == null) {
            throw new IllegalArgumentException(
                    "Parser and formatter registry are required");
        }
        this.parser = parser;
        this.formatters = formatters;
    }

    public static TextRenderer createDefault() {
        return new TextRenderer(
                new PlaceholderParser(), FormatterRegistry.defaults());
    }

    public String render(String template, TextRenderContext context) {
        return render(template, context, UnresolvedPlaceholderPolicy.FAIL);
    }

    public String render(
            String template,
            TextRenderContext context,
            UnresolvedPlaceholderPolicy unresolvedPolicy) {
        if (context == null || unresolvedPolicy == null) {
            throw new IllegalArgumentException(
                    "Text context and unresolved policy are required");
        }
        List<Part> parts = parser.parse(template);
        StringBuilder output = new StringBuilder();
        for (Part part : parts) {
            if (part.isLiteral()) {
                output.append(part.literal());
                continue;
            }
            TextRenderContext.Resolution resolution =
                    context.resolve(part.name());
            if (!resolution.found()) {
                appendUnresolved(output, part, unresolvedPolicy);
                continue;
            }
            Object value = resolution.value();
            if (part.formatter() != null) {
                output.append(formatters.format(
                        part.formatter(), value, part.argument()));
            } else if (value != null) {
                output.append(String.valueOf(value));
            }
        }
        return output.toString();
    }

    private static void appendUnresolved(
            StringBuilder output,
            Part part,
            UnresolvedPlaceholderPolicy policy) {
        if (policy == UnresolvedPlaceholderPolicy.FAIL) {
            throw new TextRenderException(
                    "Unresolved placeholder: " + part.name());
        }
        if (policy == UnresolvedPlaceholderPolicy.KEEP) {
            output.append(part.source());
        }
    }
}
