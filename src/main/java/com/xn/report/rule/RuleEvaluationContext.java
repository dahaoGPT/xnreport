package com.xn.report.rule;

import com.xn.report.dataset.DatasetContext;
import java.util.Map;

/**
 * 规则执行环境上下文容器。
 * <p>
 * 提供已执行数据集集合（{@link DatasetContext}）与只读运行时入参（runtimeParameters），
 * 供规则引擎在评估条件和解析值引用时跨数据集查询使用。
 * </p>
 */
public final class RuleEvaluationContext {

    /** 数据集上下文容器。 */
    private final DatasetContext datasets;

    /** 冻结的运行时参数字典。 */
    private final Map<String, Object> runtimeParameters;

    public RuleEvaluationContext(
            DatasetContext datasets, Map<String, Object> runtimeParameters) {
        if (datasets == null) {
            throw new IllegalArgumentException("Dataset context is required");
        }
        if (runtimeParameters == null) {
            throw new IllegalArgumentException("Runtime parameters are required");
        }
        this.datasets = datasets;
        this.runtimeParameters = RuleValues.freezeMap(runtimeParameters);
    }

    public DatasetContext getDatasets() {
        return datasets;
    }

    /**
     * 根据参数名称安全获取运行时参数值。
     *
     * @param name 参数名
     * @return 深拷贝后的参数值
     * @throws ReportException 如果参数不存在
     */
    public Object getRuntimeParameter(String name) {
        if (!runtimeParameters.containsKey(name)) {
            throw RuleErrors.reference("Missing runtime parameter: " + name);
        }
        return RuleValues.copyValue(runtimeParameters.get(name));
    }

    /**
     * 获取全部运行时参数的不可变副本字典。
     *
     * @return Map 副本
     */
    public Map<String, Object> getRuntimeParameters() {
        return RuleValues.copyMap(runtimeParameters);
    }
}
