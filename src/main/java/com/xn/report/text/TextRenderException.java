package com.xn.report.text;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;

/**
 * 文本渲染与占位符求值异常类。
 * <p>
 * 绑定统一错误码 {@link ReportErrorCode#TEXT_001}。
 * </p>
 */
public final class TextRenderException extends ReportException {

    public TextRenderException(String message) {
        super(ReportErrorCode.TEXT_001, message);
    }

    public TextRenderException(String message, Throwable cause) {
        super(ReportErrorCode.TEXT_001, message, cause);
    }
}
