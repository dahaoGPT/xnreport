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

public final class DatasetPlanner {

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
            List<String> cyclic = new ArrayList<String>();
            for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cyclic.add(entry.getKey());
                }
            }
            throw new IllegalArgumentException(
                    "Cyclic dataset dependencies involving: " + cyclic);
        }
        return Collections.unmodifiableList(planned);
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
