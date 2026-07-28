package com.xn.report.rule;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;

final class RuleErrors {

    private RuleErrors() {
    }

    static ReportException invalid(String message) {
        return new ReportException(ReportErrorCode.RULE_001, message);
    }

    static ReportException invalid(String message, Throwable cause) {
        return new ReportException(ReportErrorCode.RULE_001, message, cause);
    }

    static ReportException reference(String message) {
        return new ReportException(ReportErrorCode.RULE_002, message);
    }
}
