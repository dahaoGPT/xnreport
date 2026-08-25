package com.xn.report.config.definition;

/**
 * SQL 参数值来源渠道枚举。
 * <p>
 * <ul>
 *   <li>{@link #RUNTIME}：来自报表运行时入参 Map（如开始时间、研发中心列表等）。</li>
 *   <li>{@link #CONSTANT}：配置文件中硬编码的静态字面值。</li>
 *   <li>{@link #DATASET}：来自前置数据集执行结果（支持单值标量或多行列表自动组装为 IN 集合）。</li>
 * </ul>
 * </p>
 */
public enum ParameterSource {

    /** 外部运行时动态参数。 */
    RUNTIME,

    /** 配置文件静态常量。 */
    CONSTANT,

    /** 前置数据集执行结果。 */
    DATASET
}
