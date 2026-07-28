package com.xn.report.text;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;

public final class TextRenderException extends ReportException {

    public TextRenderException(String message) {
        super(ReportErrorCode.TEXT_001, message);
    }

    public TextRenderException(String message, Throwable cause) {
        super(ReportErrorCode.TEXT_001, message, cause);
    }
}
