package com.xn.report.entry;

/**
 * 效能报表生成组件对外暴露的标准 Java 调用门面接口。
 */
public interface ReportEntry {

    /**
     * 根据请求定义执行全流程报表生成。
     *
     * @param request 执行请求对象
     * @return 执行结果对象
     */
    ReportExecutionResult generate(ReportExecutionRequest request);
}
