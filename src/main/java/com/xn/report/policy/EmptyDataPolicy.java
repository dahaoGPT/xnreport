package com.xn.report.policy;

/**
 * 空数据处理策略枚举。
 * <p>
 * 定义当查询结果或组件数据源为空时采取的处理策略：
 * <ul>
 *   <li>{@link #SKIP}：跳过当前图表/表格/章节渲染并记录警告。</li>
 *   <li>{@link #USE_DEFAULT}：使用配置的默认值填充。</li>
 *   <li>{@link #OUTPUT_MESSAGE}：在输出文档中渲染提示文案（如“暂无数据”）。</li>
 *   <li>{@link #FAIL}：直接抛出异常中断执行。</li>
 * </ul>
 * </p>
 */
public enum EmptyDataPolicy {

    /** 跳过当前组件渲染并记录警告。 */
    SKIP,

    /** 使用配置的默认值填充。 */
    USE_DEFAULT,

    /** 在输出文档对应位置渲染提示信息。 */
    OUTPUT_MESSAGE,

    /** 抛出异常中断执行。 */
    FAIL
}
