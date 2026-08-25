package com.xn.report.text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分布分箱分析输出结果值对象。
 * <p>
 * 封装包含各分箱指标列表（{@link BinResult}）、总样本数（total）及降级状态标记。
 * </p>
 */
public final class DistributionResult {

    /** 各分箱计算结果明细列表。 */
    private final List<BinResult> bins;

    /** 参与分布统计的有效样本总量。 */
    private final int total;

    /** 是否被策略跳过。 */
    private final boolean skipped;

    /** 提示或降级文案消息。 */
    private final String message;

    DistributionResult(
            List<BinResult> bins, int total, boolean skipped, String message) {
        this.bins = Collections.unmodifiableList(
                new ArrayList<BinResult>(bins));
        this.total = total;
        this.skipped = skipped;
        this.message = message;
    }

    public List<BinResult> bins() {
        return bins;
    }

    public int total() {
        return total;
    }

    public boolean skipped() {
        return skipped;
    }

    public boolean empty() {
        return total == 0;
    }

    public String message() {
        return message;
    }

    /**
     * 单个分箱的统计结果模型。
     */
    public static final class BinResult {
        private final String id;
        private final String label;
        private final int count;
        private final BigDecimal percent;
        private final String displayLabel;

        BinResult(
                String id,
                String label,
                int count,
                BigDecimal percent,
                String displayLabel) {
            this.id = id;
            this.label = label;
            this.count = count;
            this.percent = percent;
            this.displayLabel = displayLabel;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public int count() {
            return count;
        }

        public BigDecimal percent() {
            return percent;
        }

        public String displayLabel() {
            return displayLabel;
        }
    }
}
