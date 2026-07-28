package com.xn.report.chart;

import java.util.Locale;

public final class ChartEnumValue {

    private ChartEnumValue() {
    }

    public static <E extends Enum<E>> E parse(
            Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
        return Enum.valueOf(type, normalized);
    }
}
