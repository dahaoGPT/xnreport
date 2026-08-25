package com.xn.report.execution;

/**
 * 报表生成流水线执行阶段枚举。
 */
public enum ExecutionStage {

    /** 初始化阶段。 */
    INITIALIZE,

    /** 加载配置阶段。 */
    LOAD_CONFIG,

    /** 校验配置阶段。 */
    VALIDATE_CONFIG,

    /** SQL 查询执行阶段。 */
    QUERY,

    /** 分析、清洗与计算阶段。 */
    ANALYZE,

    /** Excel 生成阶段。 */
    GENERATE_EXCEL,

    /** Word 生成阶段。 */
    GENERATE_WORD,

    /** 产物严格后置校验阶段。 */
    VALIDATE_OUTPUTS,

    /** 文件发布与归档阶段。 */
    PUBLISH,

    /** 全部完成阶段。 */
    COMPLETED
}
