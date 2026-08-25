package com.xn.report.execution;

import com.xn.report.config.ReportDefinition;

/**
 * 报表配置模型校验函数式接口。
 */
@FunctionalInterface
public interface ReportConfigValidator {

    /**
     * 校验报表定义是否合法，非法时抛出异常。
     *
     * @param definition 报表定义
     */
    void validateOrThrow(ReportDefinition definition);
}
