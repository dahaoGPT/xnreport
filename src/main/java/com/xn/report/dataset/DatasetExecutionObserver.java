package com.xn.report.dataset;

import com.xn.report.config.definition.DatasetDefinition;

/**
 * 数据集执行生命周期监听观察者接口。
 * <p>
 * 用于在单数据集执行完成后接收回调通知（主要用于测试探针与执行监控）。
 * </p>
 */
interface DatasetExecutionObserver {

    /**
     * 单个数据集执行并校验完成后的回调通知。
     *
     * @param definition 数据集配置定义
     * @param result 数据集计算结果
     */
    void afterExecution(
            DatasetDefinition definition, DatasetResult result);
}
