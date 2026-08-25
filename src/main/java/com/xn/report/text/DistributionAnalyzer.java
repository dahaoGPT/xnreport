package com.xn.report.text;

import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.DistributionDefinition.BinDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.dataset.DatasetRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 数据集分箱分布分析计算器。
 * <p>
 * 按照预设的连续分箱区间（bins），对数据行中的数值字段进行归类统计：
 * <ul>
 *   <li><b>区间闭合性与覆盖度</b>：支持 minInclusive/maxInclusive 开闭区间判定，并严格防范分箱区间重叠（overlap）或裂隙（gap）。</li>
 *   <li><b>度量计算</b>：统计每个分箱的计数值（count）、占比（percent）及按 labelMode 格式化的文本标签。</li>
 *   <li><b>空数据策略支持</b>：根据 emptyStrategy 支持 FAIL 报错或 SKIP/MESSAGE 优雅降级。</li>
 * </ul>
 * </p>
 */
public final class DistributionAnalyzer {

    /**
     * 执行分箱分布分析。
     *
     * @param rows 数据明细行列表
     * @param definition 分布分析规则定义
     * @param emptyStrategy 空数据降级策略
     * @return 分布分析综合结果 DistributionResult
     */
    public DistributionResult analyze(
            List<DatasetRow> rows,
            DistributionDefinition definition,
            NarrativeDefinition.EmptyStrategy emptyStrategy) {
        requireDefinition(definition);
        validateBins(definition.getBins());
        NarrativeDefinition.EmptyStrategy strategy = emptyStrategy == null
                ? NarrativeDefinition.EmptyStrategy.FAIL : emptyStrategy;
        if (rows == null || rows.isEmpty()) {
            if (strategy == NarrativeDefinition.EmptyStrategy.FAIL) {
                throw new TextRenderException("Distribution data is empty");
            }
            return emptyResult(definition, strategy);
        }

        int[] counts = new int[definition.getBins().size()];
        int total = 0;
        for (DatasetRow row : rows) {
            if (row == null) {
                throw new IllegalArgumentException(
                        "Distribution rows must not contain null");
            }
            if (!row.containsField(definition.getField())) {
                throw new IllegalArgumentException(
                        "Missing distribution field: " + definition.getField());
            }
            Object raw = row.getOrNull(definition.getField());
            if (raw == null) {
                continue;
            }
            BigDecimal value = numeric(raw);
            int match = matchingBin(value, definition.getBins());
            if (match < 0) {
                throw new IllegalArgumentException(
                        "Distribution bins contain a gap for value " + value);
            }
            counts[match]++;
            total++;
        }
        if (total == 0) {
            if (strategy == NarrativeDefinition.EmptyStrategy.FAIL) {
                throw new TextRenderException(
                        "Distribution has no non-null numeric values");
            }
            return emptyResult(definition, strategy);
        }
        return new DistributionResult(
                buildResults(definition, counts, total), total, false, null);
    }

    private static DistributionResult emptyResult(
            DistributionDefinition definition,
            NarrativeDefinition.EmptyStrategy strategy) {
        int[] counts = new int[definition.getBins().size()];
        return new DistributionResult(
                buildResults(definition, counts, 0),
                0,
                strategy == NarrativeDefinition.EmptyStrategy.SKIP,
                strategy == NarrativeDefinition.EmptyStrategy.SKIP
                        ? "" : "暂无分布数据");
    }

    private static List<DistributionResult.BinResult> buildResults(
            DistributionDefinition definition, int[] counts, int total) {
        List<DistributionResult.BinResult> results =
                new ArrayList<DistributionResult.BinResult>();
        for (int index = 0; index < definition.getBins().size(); index++) {
            BinDefinition bin = definition.getBins().get(index);
            BigDecimal percent = total == 0
                    ? BigDecimal.ZERO.setScale(10)
                    : BigDecimal.valueOf(counts[index])
                            .divide(BigDecimal.valueOf(total), 10,
                                    RoundingMode.HALF_UP);
            results.add(new DistributionResult.BinResult(
                    bin.getId(),
                    bin.getLabel(),
                    counts[index],
                    percent,
                    label(definition.getLabelMode(), bin.getLabel(),
                            counts[index], percent)));
        }
        return results;
    }

    private static String label(
            DistributionDefinition.LabelMode mode,
            String label,
            int count,
            BigDecimal percent) {
        DecimalFormat format = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        String percentText = format.format(
                percent.multiply(new BigDecimal("100"))) + "%";
        if (mode == DistributionDefinition.LabelMode.COUNT) {
            return label + " " + count;
        }
        if (mode == DistributionDefinition.LabelMode.PERCENT) {
            return label + " " + percentText;
        }
        return label + " " + count + " (" + percentText + ")";
    }

    private static int matchingBin(
            BigDecimal value, List<BinDefinition> bins) {
        for (int index = 0; index < bins.size(); index++) {
            if (contains(bins.get(index), value)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean contains(BinDefinition bin, BigDecimal value) {
        if (bin.getMin() != null) {
            int comparison = value.compareTo(bin.getMin());
            if (comparison < 0
                    || (comparison == 0 && !bin.isMinInclusive())) {
                return false;
            }
        }
        if (bin.getMax() != null) {
            int comparison = value.compareTo(bin.getMax());
            if (comparison > 0
                    || (comparison == 0 && !bin.isMaxInclusive())) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal numeric(Object raw) {
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(
                    "Distribution field must be numeric: " + raw);
        }
        try {
            return raw instanceof BigDecimal
                    ? (BigDecimal) raw
                    : new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Distribution field must be numeric: " + raw, exception);
        }
    }

    private static void requireDefinition(DistributionDefinition definition) {
        if (definition == null
                || !definition.hasProperty("field")
                || definition.getField() == null
                || definition.getField().trim().isEmpty()
                || !definition.hasProperty("bins")
                || definition.getBins() == null
                || definition.getBins().isEmpty()
                || !definition.hasProperty("labelMode")
                || definition.getLabelMode() == null) {
            throw new IllegalArgumentException(
                    "Distribution field, bins, and label mode are required");
        }
    }

    private static void validateBins(List<BinDefinition> bins) {
        for (int index = 0; index < bins.size(); index++) {
            BinDefinition bin = bins.get(index);
            if (bin == null
                    || !bin.hasProperty("id")
                    || bin.getId() == null
                    || bin.getId().trim().isEmpty()
                    || !bin.hasProperty("label")
                    || bin.getLabel() == null
                    || bin.getLabel().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Distribution bin id and label are required");
            }
            rejectExplicitNull(bin, "min", bin.getMin());
            rejectExplicitNull(bin, "max", bin.getMax());
            rejectExplicitNull(
                    bin, "minInclusive", bin.getMinInclusive());
            rejectExplicitNull(
                    bin, "maxInclusive", bin.getMaxInclusive());
            if (bin.getMin() != null && bin.getMax() != null) {
                int comparison = bin.getMin().compareTo(bin.getMax());
                if (comparison > 0
                        || (comparison == 0
                        && !(bin.isMinInclusive() && bin.isMaxInclusive()))) {
                    throw new IllegalArgumentException(
                            "Distribution bin is empty or reversed: " + bin.getId());
                }
            }
            for (int prior = 0; prior < index; prior++) {
                if (overlap(bins.get(prior), bin)) {
                    throw new IllegalArgumentException(
                            "Distribution bins overlap: "
                                    + bins.get(prior).getId() + " and " + bin.getId());
                }
            }
        }
    }

    private static void rejectExplicitNull(
            BinDefinition bin, String property, Object value) {
        if (bin.hasProperty(property) && value == null) {
            throw new IllegalArgumentException(
                    "Distribution bin " + property + " must not be null");
        }
    }

    private static boolean overlap(BinDefinition left, BinDefinition right) {
        return !endsBefore(
                left.getMax(), left.isMaxInclusive(),
                right.getMin(), right.isMinInclusive())
                && !endsBefore(
                right.getMax(), right.isMaxInclusive(),
                left.getMin(), left.isMinInclusive());
    }

    private static boolean endsBefore(
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
}
