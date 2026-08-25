package com.xn.report.word;

/**
 * Word 模板解析、校验与渲染异常。
 * <p>
 * 当 Word 模板缺失必要样式（Heading1~4）、缺失或重复关键占位符（如 <code>{{sections}}</code>、<code>{{chart:id}}</code>、<code>{{cover:...}}</code>）、TOC 格式错误或排版约束被破坏时抛出。
 * </p>
 */
public final class WordTemplateException extends IllegalArgumentException {

    public WordTemplateException(String message) {
        super(message);
    }

    public WordTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
