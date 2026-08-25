package com.xn.report.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本模板占位符词法解析器。
 * <p>
 * 将模板文本拆解为纯字面量（literal）与动态占位符（placeholder）片段：
 * <ul>
 *   <li>语法格式：<code>${variable|formatter:argument}</code></li>
 *   <li>支持多级点路径变量名，例如 <code>${summary.count}</code>, <code>${dataset.ds1.total|number:0.00}</code>。</li>
 *   <li>支持缺省格式化器、带参格式化器管道。</li>
 * </ul>
 * </p>
 */
public final class PlaceholderParser {

    private static final Pattern EXPRESSION = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)"
                    + "(?:\\|([A-Za-z][A-Za-z0-9]*)(?::([^{}|]*))?)?$");

    /**
     * 解析模板为不可变片段列表。
     *
     * @param template 原始模板字符串
     * @return List&lt;Part&gt; 语法片段列表
     * @throws TextRenderException 如果占位符未闭合或语法格式非法
     */
    public List<Part> parse(String template) {
        if (template == null) {
            throw new TextRenderException("Text template must not be null");
        }
        List<Part> parts = new ArrayList<Part>();
        int cursor = 0;
        while (cursor < template.length()) {
            int start = template.indexOf("${", cursor);
            if (start < 0) {
                parts.add(Part.literal(template.substring(cursor)));
                cursor = template.length();
                break;
            }
            if (start > cursor) {
                parts.add(Part.literal(template.substring(cursor, start)));
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                throw new TextRenderException(
                        "Unclosed placeholder at offset " + start);
            }
            String source = template.substring(start, end + 1);
            String body = template.substring(start + 2, end);
            Matcher matcher = EXPRESSION.matcher(body);
            if (!matcher.matches()) {
                throw new TextRenderException("Invalid placeholder: " + source);
            }
            parts.add(Part.placeholder(
                    source, matcher.group(1), matcher.group(2), matcher.group(3)));
            cursor = end + 1;
        }
        if (template.isEmpty()) {
            parts.add(Part.literal(""));
        }
        return Collections.unmodifiableList(parts);
    }

    /**
     * 模板拆解片段模型。
     */
    public static final class Part {
        private final String literal;
        private final String source;
        private final String name;
        private final String formatter;
        private final String argument;

        private Part(
                String literal,
                String source,
                String name,
                String formatter,
                String argument) {
            this.literal = literal;
            this.source = source;
            this.name = name;
            this.formatter = formatter;
            this.argument = argument;
        }

        static Part literal(String literal) {
            return new Part(literal, null, null, null, null);
        }

        static Part placeholder(
                String source, String name, String formatter, String argument) {
            return new Part(null, source, name, formatter, argument);
        }

        public boolean isLiteral() {
            return literal != null;
        }

        public String literal() {
            return literal;
        }

        public String source() {
            return source;
        }

        public String name() {
            return name;
        }

        public String formatter() {
            return formatter;
        }

        public String argument() {
            return argument;
        }
    }
}
