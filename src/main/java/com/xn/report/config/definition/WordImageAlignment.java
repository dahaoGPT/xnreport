package com.xn.report.config.definition;

public enum WordImageAlignment {
    LEFT,
    CENTER,
    RIGHT;

    public static WordImageAlignment fromConfig(String value) {
        if (value == null || value.trim().isEmpty()) {
            return CENTER;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Word image alignment must be LEFT, CENTER, or RIGHT: "
                            + value, ex);
        }
    }

    public static boolean supports(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        try {
            valueOf(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
