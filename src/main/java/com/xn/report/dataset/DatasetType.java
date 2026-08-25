package com.xn.report.dataset;

/**
 * 数据集输出形态类型枚举。
 * <p>
 * <ul>
 *   <li>{@link #SCALAR}：标量单值（最多 1 行 1 列，如总计数值、平均数等）。</li>
 *   <li>{@link #SINGLE}：单行多列记录（最多 1 行，如个人摘要、单中心概况等）。</li>
 *   <li>{@link #LIST}：多行多列表格数据（标准明细列表）。</li>
 * </ul>
 * </p>
 */
public enum DatasetType {

    /** 标量单值（1行1列）。 */
    SCALAR,

    /** 单行记录（1行多列）。 */
    SINGLE,

    /** 多行表格数据（N行多列）。 */
    LIST
}
