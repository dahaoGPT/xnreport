package com.xn.report.dataset;

import com.xn.report.config.definition.DatasetDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据集依赖拓扑排序规划器。
 * <p>
 * 基于 Kahn 算法对配置中的数据集依赖关系（{@code dependsOn}）进行有向无环图（DAG）拓扑分析：
 * <ul>
 *   <li><b>拓扑定序</b>：确保所有前置依赖数据集先于后置数据集执行。</li>
 *   <li><b>配置顺序保序</b>：同层级（入度为 0）无依赖关系的数据集，严格保持配置文件中的原始声明顺序。</li>
 *   <li><b>循环依赖精准诊断</b>：当检测到死锁环路时，利用深度优先可达性回溯算法，精准定位并输出参与循环依赖的所有数据集 ID 列表。</li>
 * </ul>
 * </p>
 */
public final class DatasetPlanner {

    /**
     * 对数据集列表执行依赖分析并规划拓扑执行顺序。
     *
     * @param datasets 原始声明的数据集配置列表
     * @return 经过拓扑排序后的可执行数据集列表
     * @throws IllegalArgumentException 如果存在重复 ID、未知依赖或循环依赖
     */
    public List<DatasetDefinition> plan(List<DatasetDefinition> datasets) {
        if (datasets == null) {
            throw new IllegalArgumentException("Datasets must not be null");
        }

        Map<String, DatasetDefinition> byId =
                new LinkedHashMap<String, DatasetDefinition>();
        Map<String, Integer> order = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < datasets.size(); index++) {
            DatasetDefinition dataset = datasets.get(index);
            if (dataset == null) {
                throw new IllegalArgumentException(
                        "Dataset definition must not be null at index " + index);
            }
            String id = requireId(dataset.getId());
            if (byId.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate dataset id: " + id);
            }
            byId.put(id, dataset);
            order.put(id, index);
        }

        Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
        Map<String, List<String>> dependents =
                new LinkedHashMap<String, List<String>>();
        for (String id : byId.keySet()) {
            indegree.put(id, 0);
            dependents.put(id, new ArrayList<String>());
        }

        for (DatasetDefinition dataset : byId.values()) {
            List<String> dependencies = dataset.getDependsOn();
            if (dependencies == null) {
                dependencies = Collections.emptyList();
            }
            Set<String> uniqueDependencies = new LinkedHashSet<String>();
            for (String dependency : dependencies) {
                if (!byId.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Dataset " + dataset.getId()
                                    + " depends on unknown dataset " + dependency);
                }
                if (uniqueDependencies.add(dependency)) {
                    indegree.put(dataset.getId(), indegree.get(dataset.getId()) + 1);
                    dependents.get(dependency).add(dataset.getId());
                }
            }
        }

        List<String> currentLevel = new ArrayList<String>();
        for (String id : byId.keySet()) {
            if (indegree.get(id) == 0) {
                currentLevel.add(id);
            }
        }

        List<DatasetDefinition> planned =
                new ArrayList<DatasetDefinition>(datasets.size());
        while (!currentLevel.isEmpty()) {
            List<String> nextLevel = new ArrayList<String>();
            for (String id : currentLevel) {
                planned.add(byId.get(id));
                for (String dependent : dependents.get(id)) {
                    int remaining = indegree.get(dependent) - 1;
                    indegree.put(dependent, remaining);
                    if (remaining == 0) {
                        nextLevel.add(dependent);
                    }
                }
            }
            sortByConfigurationOrder(nextLevel, order);
            currentLevel = nextLevel;
        }

        if (planned.size() != datasets.size()) {
            List<String> cyclic = findCycleMembers(byId, indegree);
            throw new IllegalArgumentException(
                    "Cyclic dataset dependencies involving: " + cyclic);
        }
        return Collections.unmodifiableList(planned);
    }

    /**
     * 提取参与循环依赖的节点集合。
     */
    private static List<String> findCycleMembers(
            Map<String, DatasetDefinition> byId,
            Map<String, Integer> indegree) {
        Set<String> residual = new LinkedHashSet<String>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() > 0) {
                residual.add(entry.getKey());
            }
        }

        List<String> cycleMembers = new ArrayList<String>();
        for (String id : byId.keySet()) {
            if (residual.contains(id)
                    && reaches(id, id, byId, residual, new LinkedHashSet<String>(), true)) {
                cycleMembers.add(id);
            }
        }
        return cycleMembers;
    }

    /**
     * 深度优先判断是否存在回到起始节点的闭环路径。
     */
    private static boolean reaches(
            String current,
            String target,
            Map<String, DatasetDefinition> byId,
            Set<String> residual,
            Set<String> visited,
            boolean firstStep) {
        if (!firstStep && current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        List<String> dependencies = byId.get(current).getDependsOn();
        if (dependencies == null) {
            return false;
        }
        for (String dependency : dependencies) {
            if (residual.contains(dependency)
                    && reaches(
                            dependency,
                            target,
                            byId,
                            residual,
                            visited,
                            false)) {
                return true;
            }
        }
        return false;
    }

    private static void sortByConfigurationOrder(
            List<String> ids, final Map<String, Integer> order) {
        Collections.sort(ids, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return order.get(left).compareTo(order.get(right));
            }
        });
    }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset id must not be blank");
        }
        return id;
    }
}
