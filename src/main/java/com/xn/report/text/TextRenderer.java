package com.xn.report.text;

import com.xn.report.text.PlaceholderParser.Part;
import java.util.List;

/**
 * 动态文本与占位符求值渲染核心驱动器。
 * <p>
 * 接收模板字符串与 {@link TextRenderContext}，通过 {@link PlaceholderParser} 进行语法分词，
 * 依次解析变量并经由 {@link FormatterRegistry} 执行管道格式化，拼装输出最终文本。
 * </p>
 */
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

    /**
     * 创建搭载默认解析器与格式化器的渲染器实例。
     */
    public static TextRenderer createDefault() {
        return new TextRenderer(
                new PlaceholderParser(), FormatterRegistry.defaults());
    }

    /**
     * 渲染文本模板（未匹配变量默认抛出异常）。
     *
     * @param template 模板字符串
     * @param context 渲染上下文
     * @return 渲染后的文本
     */
    public String render(String template, TextRenderContext context) {
        return render(template, context, UnresolvedPlaceholderPolicy.FAIL);
    }

    /**
     * 按照指定的未命中策略渲染文本模板。
     *
     * @param template 模板字符串
     * @param context 渲染上下文
     * @param unresolvedPolicy 未解析占位符策略（FAIL, KEEP, EMPTY）
     * @return 渲染后的文本
     */
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
