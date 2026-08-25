package com.xn.report.rule;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;

/**
 * 规则引擎异常工厂助手类。
 * <p>
 * 集中构造 {@link ReportErrorCode#RULE_001}（规则配置语法错误）与 {@link ReportErrorCode#RULE_002}（规则字段/参数引用错误）的标准 ReportException。
 * </p>
 */
final class RuleErrors {

    private RuleErrors() {
    }

    /**
     * 构建 RULE-001 规则配置语法错误异常。
     *
     * @param message 错误说明文本
     * @return ReportException 实例
     */
    static ReportException invalid(String message) {
        return new ReportException(ReportErrorCode.RULE_001, message);
    }

    /**
     * 构建包含根因的 RULE-001 规则配置语法错误异常。
     *
     * @param message 错误说明文本
     * @param cause 异常根因
     * @return ReportException 实例
     */
    static ReportException invalid(String message, Throwable cause) {
        return new ReportException(ReportErrorCode.RULE_001, message, cause);
    }

    /**
     * 构建 RULE-002 规则引用未定义字段/参数异常。
     *
     * @param message 错误说明文本
     * @return ReportException 实例
     */
    static ReportException reference(String message) {
        return new ReportException(ReportErrorCode.RULE_002, message);
    }
}
