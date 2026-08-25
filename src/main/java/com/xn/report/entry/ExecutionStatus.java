package com.xn.report.entry;

/**
 * 报表执行最终状态枚举。
 */
public enum ExecutionStatus {

    /** 执行成功（无任何警告）。 */
    SUCCESS,

    /** 执行成功（伴随业务警告，如空数据跳过）。 */
    SUCCESS_WITH_WARNINGS,

    /** 执行失败。 */
    FAILED
}
