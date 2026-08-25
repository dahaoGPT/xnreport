package com.xn.report.dataset;

import com.xn.report.config.ReportDefinition;
import java.util.Map;

/**
 * 数据集查询与执行服务接口。
 * <p>
 * 负责报表全部数据集的批量并发或拓扑顺序执行、Schema 校验与策略降级处理。
 * </p>
 */
public interface DatasetQueryService {

    /**
     * 顺序执行报表中声明的所有数据集。
     *
     * @param definition 报表完整配置定义
     * @param runtimeParameters 运行时动态参数
     * @return 包含所有执行结果的数据集上下文 DatasetContext
     */
    DatasetContext executeAll(
            ReportDefinition definition, Map<String, Object> runtimeParameters);

    /**
     * 执行所有数据集并附带收集执行期间产生的策略告警日志。
     *
     * @param definition 报表完整配置定义
     * @param runtimeParameters 运行时动态参数
     * @return 包含 DatasetContext 与告警列表的 QueryOutcome 对象
     */
    default QueryOutcome executeAllWithWarnings(
            ReportDefinition definition, Map<String, Object> runtimeParameters) {
        return new QueryOutcome(executeAll(definition, runtimeParameters),
                java.util.Collections.<com.xn.report.policy.ReportWarning>emptyList());
    }
}
