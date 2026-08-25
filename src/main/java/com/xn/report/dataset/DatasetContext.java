package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集执行上下文容器。
 * <p>
 * 存储报表内所有已执行完成的数据集结果（{@link DatasetResult}）。
 * 保证内部映射关系不可变，供后置数据集参数绑定、内存转换、规则引擎及模板渲染统一查询使用。
 * </p>
 */
public final class DatasetContext {

    /** 数据集 ID 到结果对象的不可变字典映射。 */
    private final Map<String, DatasetResult> results;

    /** 保持拓扑执行顺序的数据集 ID 列表。 */
    private final List<String> ids;

    private DatasetContext(Map<String, DatasetResult> source) {
        LinkedHashMap<String, DatasetResult> copy =
                new LinkedHashMap<String, DatasetResult>(source);
        this.results = Collections.unmodifiableMap(copy);
        this.ids = Collections.unmodifiableList(
                new ArrayList<String>(copy.keySet()));
    }

    /**
     * 创建上下文构建器。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 根据数据集 ID 获取对应的执行结果。
     *
     * @param id 数据集唯一标识
     * @return DatasetResult 实例
     * @throws IllegalArgumentException 如果指定 ID 的数据集不存在
     */
    public DatasetResult get(String id) {
        DatasetResult result = results.get(id);
        if (result == null) {
            throw new IllegalArgumentException("Missing dataset: " + id);
        }
        return result;
    }

    /**
     * 检查是否包含指定 ID 的数据集结果。
     *
     * @param id 数据集唯一标识
     * @return true 表示存在，false 表示不存在
     */
    public boolean contains(String id) {
        return results.containsKey(id);
    }

    /**
     * 获取所有数据集 ID 列表（保持插入/执行拓扑顺序）。
     *
     * @return 数据集 ID 不可变列表
     */
    public List<String> ids() {
        return ids;
    }

    /**
     * 获取数据集 ID 到结果对象的不可变 Map 字典。
     *
     * @return 结果字典映射
     */
    public Map<String, DatasetResult> asMap() {
        return results;
    }

    /**
     * 数据集上下文构建器。
     */
    public static final class Builder {

        private final Map<String, DatasetResult> results =
                new LinkedHashMap<String, DatasetResult>();

        /**
         * 存入一个已执行完成的数据集结果。
         *
         * @param result 数据集结果对象
         * @return 当前构建器实例
         */
        public Builder put(DatasetResult result) {
            if (result == null) {
                throw new IllegalArgumentException("Dataset result must not be null");
            }
            if (results.containsKey(result.id())) {
                throw new IllegalArgumentException(
                        "Duplicate dataset id: " + result.id());
            }
            results.put(result.id(), result);
            return this;
        }

        /**
         * 基于当前已有结果构建快照视图（用于后续数据集执行时传递前置上下文）。
         *
         * @return 当前状态的 DatasetContext 实例
         */
        public DatasetContext buildView() {
            return new DatasetContext(results);
        }

        /**
         * 完成构建并返回不可变上下文。
         *
         * @return 最终 DatasetContext 实例
         */
        public DatasetContext build() {
            return new DatasetContext(results);
        }
    }
}
