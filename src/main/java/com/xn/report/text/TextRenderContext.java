package com.xn.report.text;

import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetType;
import com.xn.report.rule.RuleResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本与智能叙述句渲染统一环境上下文容器。
 * <p>
 * 汇聚多维数据源，并实现作用域变量名解析路由：
 * <ul>
 *   <li><code>summary.*</code>：当前叙述句分析度量字典。</li>
 *   <li><code>runtime.*</code>：报表任务全局运行时入参。</li>
 *   <li><code>dataset.{id}.{field}</code>：指定数据集字段（SCALAR / SINGLE）。</li>
 *   <li><code>rule.{id}.matchedCount</code> 或 <code>rule.{id}.summary.{key}</code>：异常规则计算产物。</li>
 *   <li>无前缀字段：在当前行、summary、runtime、单行数据集中自动查找匹配（存在二义性时强制要求前缀限定）。</li>
 * </ul>
 * </p>
 */
public final class TextRenderContext {

    private final DatasetRow currentRow;
    private final Map<String, Object> summary;
    private final Map<String, Object> runtime;
    private final DatasetContext datasets;
    private final Map<String, RuleResult> rules;

    private TextRenderContext(Builder builder) {
        this.currentRow = builder.currentRow == null
                ? DatasetRow.empty() : builder.currentRow;
        this.summary = TextValueSnapshot.map(builder.summary);
        this.runtime = TextValueSnapshot.map(builder.runtime);
        this.datasets = builder.datasets == null
                ? DatasetContext.builder().build() : builder.datasets;
        this.rules = Collections.unmodifiableMap(
                new LinkedHashMap<String, RuleResult>(builder.rules == null
                        ? Collections.<String, RuleResult>emptyMap()
                        : builder.rules));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 派生携带全新 summary 度量字典的上下文副本。
     */
    public TextRenderContext withSummary(Map<String, Object> values) {
        return builder()
                .currentRow(currentRow)
                .summary(values)
                .runtime(runtime)
                .datasets(datasets)
                .rules(rules)
                .build();
    }

    /**
     * 依据点路径路由解析占位符变量值。
     *
     * @param name 变量表达式名称
     * @return 解析结果包装对象 Resolution
     */
    Resolution resolve(String name) {
        if (name.startsWith("summary.")) {
            return fromMap(summary, name.substring("summary.".length()));
        }
        if (name.startsWith("runtime.")) {
            return fromMap(runtime, name.substring("runtime.".length()));
        }
        if (name.startsWith("dataset.")) {
            return fromDataset(name);
        }
        if (name.startsWith("rule.")) {
            return fromRule(name);
        }
        if (name.indexOf('.') >= 0) {
            return Resolution.missing();
        }
        int matchingScopes = currentRow.containsField(name) ? 1 : 0;
        matchingScopes += summary.containsKey(name) ? 1 : 0;
        matchingScopes += runtime.containsKey(name) ? 1 : 0;
        for (DatasetResult result : datasets.asMap().values()) {
            if (result.type() == DatasetType.SINGLE
                    && result.schema().containsField(name)) {
                matchingScopes++;
            } else if (result.type() == DatasetType.SCALAR
                    && (result.schema().containsField(name)
                    || "value".equals(name))) {
                matchingScopes++;
            }
        }
        if (matchingScopes > 1) {
            throw new TextRenderException(
                    "Ambiguous unqualified placeholder: " + name);
        }
        if (currentRow.containsField(name)) {
            return Resolution.found(currentRow.getOrNull(name));
        }
        if (matchingScopes == 1) {
            throw new TextRenderException(
                    "Cross-scope placeholder must be qualified: " + name);
        }
        return Resolution.missing();
    }

    DatasetContext datasets() {
        return datasets;
    }

    Map<String, Object> runtime() {
        return runtime;
    }

    private Resolution fromDataset(String name) {
        String reference = name.substring("dataset.".length());
        int separator = reference.indexOf('.');
        if (separator <= 0 || separator == reference.length() - 1) {
            return Resolution.missing();
        }
        String datasetId = reference.substring(0, separator);
        String field = reference.substring(separator + 1);
        if (!datasets.contains(datasetId)) {
            return Resolution.missing();
        }
        DatasetResult result = datasets.get(datasetId);
        Object value;
        if (result.type() == DatasetType.SCALAR) {
            if (!result.schema().containsField(field)
                    && !"value".equals(field)) {
                return Resolution.missing();
            }
            value = result.scalar();
        } else if (result.type() == DatasetType.SINGLE) {
            DatasetRow row = result.single();
            if (row == null || !row.containsField(field)) {
                return Resolution.missing();
            }
            value = row.getOrNull(field);
        } else {
            throw new TextRenderException(
                    "Dataset placeholder requires SCALAR or SINGLE dataset: "
                            + datasetId);
        }
        return Resolution.found(value);
    }

    private Resolution fromRule(String name) {
        String reference = name.substring("rule.".length());
        int separator = reference.indexOf('.');
        if (separator <= 0 || separator == reference.length() - 1) {
            return Resolution.missing();
        }
        RuleResult result = rules.get(reference.substring(0, separator));
        if (result == null) {
            return Resolution.missing();
        }
        String property = reference.substring(separator + 1);
        if ("matchedCount".equals(property)) {
            return Resolution.found(Integer.valueOf(
                    result.getMatchedRows().size()));
        }
        String prefix = "summary.";
        if (property.startsWith(prefix)
                && property.length() > prefix.length()) {
            return fromMap(result.getSummaryValues(),
                    property.substring(prefix.length()));
        }
        return Resolution.missing();
    }

    private static Resolution fromMap(Map<String, Object> values, String key) {
        return values.containsKey(key)
                ? Resolution.found(values.get(key)) : Resolution.missing();
    }

    /**
     * 变量解析匹配状态值对象。
     */
    static final class Resolution {
        private final boolean found;
        private final Object value;

        private Resolution(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }

        static Resolution found(Object value) {
            return new Resolution(true, value);
        }

        static Resolution missing() {
            return new Resolution(false, null);
        }

        boolean found() {
            return found;
        }

        Object value() {
            return value;
        }
    }

    /**
     * 上下文构造器。
     */
    public static final class Builder {
        private DatasetRow currentRow;
        private Map<String, Object> summary;
        private Map<String, Object> runtime;
        private DatasetContext datasets;
        private Map<String, RuleResult> rules;

        public Builder currentRow(DatasetRow currentRow) {
            this.currentRow = currentRow;
            return this;
        }

        public Builder summary(Map<String, Object> summary) {
            this.summary = summary;
            return this;
        }

        public Builder runtime(Map<String, Object> runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder datasets(DatasetContext datasets) {
            this.datasets = datasets;
            return this;
        }

        public Builder rules(Map<String, RuleResult> rules) {
            this.rules = rules;
            return this;
        }

        public TextRenderContext build() {
            return new TextRenderContext(this);
        }
    }
}
