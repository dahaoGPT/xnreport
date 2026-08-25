package com.xn.report.dataset;

import com.xn.report.policy.ReportWarning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 数据集执行产物综合结果封装。
 * <p>
 * 包含已执行的数据集上下文（{@link DatasetContext}）以及执行期间累积触发的所有策略告警事件列表（{@link ReportWarning}）。
 * </p>
 */
public final class QueryOutcome {

    /** 数据集上下文容器。 */
    private final DatasetContext datasets;

    /** 策略告警列表。 */
    private final List<ReportWarning> warnings;

    /**
     * 构造查询结果封装。
     *
     * @param datasets 数据集上下文，不可为 null
     * @param warnings 告警列表
     */
    public QueryOutcome(DatasetContext datasets, List<ReportWarning> warnings) {
        this.datasets = Objects.requireNonNull(datasets, "datasets");
        this.warnings = Collections.unmodifiableList(
                new ArrayList<ReportWarning>(warnings == null
                        ? Collections.<ReportWarning>emptyList() : warnings));
    }

    /**
     * 获取数据集上下文容器。
     *
     * @return DatasetContext 实例
     */
    public DatasetContext getDatasets() {
        return datasets;
    }

    /**
     * 获取执行期间收集的全部策略告警列表。
     *
     * @return 不可变告警列表
     */
    public List<ReportWarning> getWarnings() {
        return warnings;
    }
}
