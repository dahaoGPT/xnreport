package com.xn.report.config.definition;

/**
 * Word 图片/图表插入段落对齐方式枚举。
 * <p>
 * 支持 LEFT（左对齐）、CENTER（居中对齐）、RIGHT（右对齐）。
 * </p>
 */
public enum WordImageAlignment {

    /** 居左对齐。 */
    LEFT,

    /** 居中对齐（默认）。 */
    CENTER,

    /** 居右对齐。 */
    RIGHT;

    /**
     * 从配置字符串安全解析对齐枚举（默认返回 CENTER）。
     *
     * @param value 配置字符串
     * @return 对齐枚举
     */
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

    /**
     * 判断配置值是否为受支持的对齐模式。
     *
     * @param value 待检查字符串
     * @return true 表示有效或为空，false 表示非法
     */
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
