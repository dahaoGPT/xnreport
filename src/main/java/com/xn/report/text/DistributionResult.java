package com.xn.report.text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DistributionResult {

    private final List<BinResult> bins;
    private final int total;
    private final boolean skipped;
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
