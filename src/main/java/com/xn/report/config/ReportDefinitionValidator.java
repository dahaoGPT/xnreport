package com.xn.report.config;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.excel.ExcelSheetNameRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ReportDefinitionValidator {

    static final int MAX_SECTION_TITLE_UTF16_LENGTH = 255;

    private static final Pattern DATASET_ID =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");
    private static final Set<String> COMPONENT_TYPES =
            unmodifiableSet("SCENARIO", "KEY_FACTORS", "FIXED_TEXT", "RULE_TEXT",
                    "CHART", "TABLE", "UNIT", "ATTACHMENT");
    private static final Set<String> TEXT_COMPONENT_TYPES =
            unmodifiableSet("SCENARIO", "KEY_FACTORS", "FIXED_TEXT", "UNIT", "ATTACHMENT");
    private static final Set<String> EMPTY_STRATEGIES =
            unmodifiableSet("KEEP", "SHOW_EMPTY", "SKIP");
    private static final Set<String> NARRATIVE_SOURCE_TYPES =
            unmodifiableSet("FIXED_TEMPLATE", "RULE_GENERATED");

    public ValidationResult validate(ReportDefinition definition) {
        ValidationResult result = new ValidationResult();
        if (definition == null) {
            result.add("CFG-DEFINITION", "$", "Report definition is required");
            return result;
        }

        requireText(result, definition.getSchemaVersion(), "CFG-SCHEMA-VERSION",
                "$.schemaVersion", "schemaVersion is required");
        if (definition.getReport() == null
                || !hasText(definition.getReport().getCode())
                || !hasText(definition.getReport().getName())) {
            result.add("CFG-REPORT", "$.report",
                    "report with non-blank code and name is required");
        }

        List<DatasetDefinition> datasets = safeList(definition.getDatasets());
        if (datasets.isEmpty()) {
            result.add("CFG-DATASETS", "$.datasets", "At least one dataset is required");
        }

        Set<String> datasetIds = validateDatasets(datasets, result);
        validateDependencies(datasets, datasetIds, result);
        validateDependencyCycles(datasets, datasetIds, result);
        Set<String> narrativeIds =
                validateNarratives(definition.getNarratives(), datasetIds, result);
        validateWord(definition.getWord(), narrativeIds, result);
        return result;
    }

    private Set<String> validateDatasets(
            List<DatasetDefinition> datasets, ValidationResult result) {
        Set<String> ids = new LinkedHashSet<String>();
        List<String> sheetNames = new ArrayList<String>();
        for (int index = 0; index < datasets.size(); index++) {
            DatasetDefinition dataset = datasets.get(index);
            String path = "$.datasets[" + index + "]";
            if (dataset == null) {
                result.add("CFG-DATASET", path, "Dataset must not be null");
                continue;
            }

            String id = dataset.getId();
            if (!hasText(id) || !DATASET_ID.matcher(id).matches()) {
                result.add("CFG-DATASET-ID", path + ".id",
                        "Dataset id must match " + DATASET_ID.pattern());
            } else if (!ids.add(id)) {
                result.add("CFG-DUPLICATE-DATASET", path + ".id",
                        "Duplicate dataset id: " + id);
            }

            boolean hasSqlFile = hasText(dataset.getSqlFile());
            boolean hasSql = hasText(dataset.getSql());
            if (hasSqlFile == hasSql) {
                result.add("CFG-SQL-SOURCE", path,
                        "Exactly one of sqlFile and sql must be configured");
            }
            validateSheetName(dataset.getSheetName(), path + ".sheetName",
                    sheetNames, result);
        }
        return ids;
    }

    private void validateSheetName(
            String sheetName,
            String path,
            List<String> seen,
            ValidationResult result) {
        if (!hasText(sheetName)) {
            result.add("CFG-SHEET-NAME-REQUIRED", path, "sheetName is required");
            return;
        }
        try {
            ExcelSheetNameRules.validate(sheetName);
        } catch (IllegalArgumentException exception) {
            String code = sheetName.length() > 31
                    ? "CFG-SHEET-NAME-LENGTH" : "CFG-SHEET-NAME-ILLEGAL";
            result.add(code, path, exception.getMessage());
        }
        if (ExcelSheetNameRules.containsIgnoreCase(seen, sheetName)) {
            result.add("CFG-DUPLICATE-SHEET-NAME", path,
                    "Duplicate sheetName: " + sheetName);
        }
        seen.add(sheetName);
    }

    private void validateDependencies(
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            ValidationResult result) {
        for (int datasetIndex = 0; datasetIndex < datasets.size(); datasetIndex++) {
            DatasetDefinition dataset = datasets.get(datasetIndex);
            if (dataset == null) {
                continue;
            }
            List<String> dependencies = safeList(dataset.getDependsOn());
            for (int dependencyIndex = 0;
                    dependencyIndex < dependencies.size();
                    dependencyIndex++) {
                String dependency = dependencies.get(dependencyIndex);
                if (!hasText(dependency) || !datasetIds.contains(dependency)) {
                    result.add("CFG-UNKNOWN-DEPENDENCY",
                            "$.datasets[" + datasetIndex + "].dependsOn["
                                    + dependencyIndex + "]",
                            "Unknown dataset dependency: " + dependency);
                }
            }
        }
    }

    private void validateDependencyCycles(
            List<DatasetDefinition> datasets,
            Set<String> datasetIds,
            ValidationResult result) {
        Map<String, List<String>> graph = new LinkedHashMap<String, List<String>>();
        for (String id : datasetIds) {
            graph.put(id, new ArrayList<String>());
        }
        for (DatasetDefinition dataset : datasets) {
            if (dataset == null || !hasText(dataset.getId())
                    || !graph.containsKey(dataset.getId())) {
                continue;
            }
            for (String dependency : safeList(dataset.getDependsOn())) {
                if (graph.containsKey(dependency)) {
                    graph.get(dataset.getId()).add(dependency);
                }
            }
        }

        Map<String, Integer> states = new LinkedHashMap<String, Integer>();
        for (String id : graph.keySet()) {
            if (hasCycle(id, graph, states)) {
                result.add("CFG-DEPENDENCY-CYCLE", "$.datasets",
                        "Dataset dependency graph contains a cycle involving " + id);
                return;
            }
        }
    }

    private boolean hasCycle(
            String id,
            Map<String, List<String>> graph,
            Map<String, Integer> states) {
        Integer state = states.get(id);
        if (Integer.valueOf(1).equals(state)) {
            return true;
        }
        if (Integer.valueOf(2).equals(state)) {
            return false;
        }

        states.put(id, 1);
        for (String dependency : graph.get(id)) {
            if (hasCycle(dependency, graph, states)) {
                return true;
            }
        }
        states.put(id, 2);
        return false;
    }

    private Set<String> validateNarratives(
            List<NarrativeDefinition> narratives,
            Set<String> datasetIds,
            ValidationResult result) {
        Set<String> narrativeIds = new LinkedHashSet<String>();
        List<NarrativeDefinition> safeNarratives = safeList(narratives);
        for (int index = 0; index < safeNarratives.size(); index++) {
            NarrativeDefinition narrative = safeNarratives.get(index);
            String path = "$.narratives[" + index + "]";
            if (narrative == null) {
                result.add("CFG-NARRATIVE", path, "Narrative must not be null");
                continue;
            }
            if (!hasText(narrative.getId())) {
                result.add("CFG-NARRATIVE-ID", path + ".id", "Narrative id is required");
            } else if (!narrativeIds.add(narrative.getId())) {
                result.add("CFG-DUPLICATE-NARRATIVE", path + ".id",
                        "Duplicate narrative id: " + narrative.getId());
            }
            if (!NARRATIVE_SOURCE_TYPES.contains(narrative.getSourceType())) {
                result.add("CFG-NARRATIVE-SOURCE-TYPE", path + ".sourceType",
                        "Narrative sourceType must be FIXED_TEMPLATE or RULE_GENERATED");
            }
            if (hasText(narrative.getDataset())
                    && !datasetIds.contains(narrative.getDataset())) {
                result.add("CFG-UNKNOWN-DATASET-REFERENCE", path + ".dataset",
                        "Unknown dataset reference: " + narrative.getDataset());
            }
            validateDistribution(narrative.getDistribution(),
                    path + ".distribution", result);
        }
        return narrativeIds;
    }

    private void validateDistribution(
            DistributionDefinition distribution,
            String path,
            ValidationResult result) {
        if (distribution == null) {
            return;
        }
        List<BinDefinition> bins = safeList(distribution.getBins());
        for (int index = 0; index < bins.size(); index++) {
            BinDefinition bin = bins.get(index);
            if (bin == null) {
                result.add("CFG-DISTRIBUTION-BIN", path + ".bins[" + index + "]",
                        "Distribution bin must not be null");
                continue;
            }
            if (isEmptyOrReversed(bin)) {
                result.add("CFG-DISTRIBUTION-BOUNDS", path + ".bins[" + index + "]",
                        "Distribution bin bounds do not define a non-empty interval");
            }
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                BinDefinition other = bins.get(otherIndex);
                if (other != null && intervalsOverlap(bin, other)) {
                    result.add("CFG-DISTRIBUTION-OVERLAP", path + ".bins[" + index + "]",
                            "Distribution bins overlap: "
                                    + other.getId() + " and " + bin.getId());
                }
            }
        }
    }

    private boolean isEmptyOrReversed(BinDefinition bin) {
        BigDecimal min = bin.getMin();
        BigDecimal max = bin.getMax();
        if (min == null || max == null) {
            return false;
        }
        int comparison = min.compareTo(max);
        return comparison > 0
                || (comparison == 0 && !(bin.isMinInclusive() && bin.isMaxInclusive()));
    }

    private boolean intervalsOverlap(BinDefinition left, BinDefinition right) {
        if (endsBefore(
                left.getMax(), left.isMaxInclusive(),
                right.getMin(), right.isMinInclusive())) {
            return false;
        }
        return !endsBefore(
                right.getMax(), right.isMaxInclusive(),
                left.getMin(), left.isMinInclusive());
    }

    private boolean endsBefore(
            BigDecimal upper,
            boolean upperInclusive,
            BigDecimal lower,
            boolean lowerInclusive) {
        if (upper == null || lower == null) {
            return false;
        }
        int comparison = upper.compareTo(lower);
        return comparison < 0
                || (comparison == 0 && !(upperInclusive && lowerInclusive));
    }

    private void validateWord(
            WordDefinition word,
            Set<String> narrativeIds,
            ValidationResult result) {
        if (word == null) {
            return;
        }
        if (word.getToc() != null
                && (word.getToc().getMaxLevel() < 1 || word.getToc().getMaxLevel() > 4)) {
            result.add("CFG-TOC-LEVEL", "$.word.toc.maxLevel",
                    "TOC maxLevel must be between 1 and 4");
        }
        Set<String> sectionIds = new LinkedHashSet<String>();
        Set<WordSectionDefinition> visited = Collections.newSetFromMap(
                new IdentityHashMap<WordSectionDefinition, Boolean>());
        List<WordSectionDefinition> sections = safeList(word.getSections());
        for (int index = 0; index < sections.size(); index++) {
            validateSection(sections.get(index), null, 1,
                    "$.word.sections[" + index + "]",
                    sectionIds, narrativeIds, visited, result);
        }
    }

    private void validateSection(
            WordSectionDefinition section,
            Integer parentLevel,
            int depth,
            String path,
            Set<String> sectionIds,
            Set<String> narrativeIds,
            Set<WordSectionDefinition> visited,
            ValidationResult result) {
        if (section == null) {
            result.add("CFG-SECTION", path, "Word section must not be null");
            return;
        }
        if (depth > 4) {
            result.add("CFG-SECTION-DEPTH", path,
                    "Word section nesting must not exceed four levels");
            return;
        }
        if (!visited.add(section)) {
            result.add("CFG-SECTION-CYCLE", path, "Word section tree contains a cycle");
            return;
        }
        if (!hasText(section.getId())) {
            result.add("CFG-SECTION-ID", path + ".id", "Word section id is required");
        } else if (!sectionIds.add(section.getId())) {
            result.add("CFG-DUPLICATE-SECTION", path + ".id",
                    "Duplicate word section id: " + section.getId());
        }
        if (!hasText(section.getTitle())) {
            result.add("CFG-SECTION-TITLE", path + ".title",
                    "Word section title is required");
        } else if (section.getTitle().length() > MAX_SECTION_TITLE_UTF16_LENGTH) {
            result.add("CFG-SECTION-TITLE-LENGTH", path + ".title",
                    "Word section title must not exceed "
                            + MAX_SECTION_TITLE_UTF16_LENGTH
                            + " Java UTF-16 code units");
        }
        int level = section.getLevel();
        if (level < 1 || level > 4) {
            result.add("CFG-SECTION-LEVEL", path + ".level",
                    "Word section level must be between 1 and 4");
        }
        if (parentLevel != null && level <= parentLevel.intValue()) {
            result.add("CFG-SECTION-HIERARCHY", path + ".level",
                    "Child section level must be greater than its parent level");
        }
        if (section.getEmptyStrategy() != null
                && !EMPTY_STRATEGIES.contains(section.getEmptyStrategy())) {
            result.add("CFG-EMPTY-STRATEGY", path + ".emptyStrategy",
                    "emptyStrategy must be KEEP, SHOW_EMPTY, or SKIP");
        }

        List<WordComponentDefinition> components = safeList(section.getComponents());
        for (int index = 0; index < components.size(); index++) {
            validateComponent(components.get(index),
                    path + ".components[" + index + "]", narrativeIds, result);
        }
        List<WordSectionDefinition> children = safeList(section.getChildren());
        for (int index = 0; index < children.size(); index++) {
            validateSection(children.get(index), level, depth + 1,
                    path + ".children[" + index + "]",
                    sectionIds, narrativeIds, visited, result);
        }
    }

    private void validateComponent(
            WordComponentDefinition component,
            String path,
            Set<String> narrativeIds,
            ValidationResult result) {
        if (component == null) {
            result.add("CFG-COMPONENT", path, "Word component must not be null");
            return;
        }
        String type = component.getType();
        if (!COMPONENT_TYPES.contains(type)) {
            result.add("CFG-COMPONENT-TYPE", path + ".type",
                    "Unknown word component type: " + type);
            return;
        }
        if (TEXT_COMPONENT_TYPES.contains(type) && !hasText(component.getText())) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".text",
                    type + " requires non-blank text");
        } else if ("RULE_TEXT".equals(type)
                && (!hasText(component.getNarrativeId())
                || !narrativeIds.contains(component.getNarrativeId()))) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".narrativeId",
                    "RULE_TEXT references an unknown narrative: "
                            + component.getNarrativeId());
        } else if ("CHART".equals(type) && !hasText(component.getChartId())) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".chartId",
                    "CHART requires a non-blank chartId");
        } else if ("TABLE".equals(type) && !hasText(component.getTableId())) {
            result.add("CFG-COMPONENT-REFERENCE", path + ".tableId",
                    "TABLE requires a non-blank tableId");
        }
    }

    private void requireText(
            ValidationResult result,
            String value,
            String code,
            String path,
            String message) {
        if (!hasText(value)) {
            result.add(code, path, message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }

    private static Set<String> unmodifiableSet(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
