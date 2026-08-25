package com.xn.report.policy;

/**
 * 警告事件收集接收器函数式接口。
 * <p>
 * 用于在策略降级、字段跳过、默认值替换等非致命场景下收集 {@link ReportWarning} 警告事件。
 * </p>
 */
@FunctionalInterface
public interface WarningSink {

    /**
     * 接收并处理一个策略警告。
     *
     * @param warning 警告对象
     */
    void accept(ReportWarning warning);

    /**
     * 创建一个忽略所有警告的空实现接收器。
     *
     * @return 忽略警告的接收器实例
     */
    static WarningSink ignoring() {
        return warning -> {
        };
    }
}
