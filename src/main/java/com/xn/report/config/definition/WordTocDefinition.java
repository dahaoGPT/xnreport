package com.xn.report.config.definition;

/**
 * Word 目录（TOC - Table of Contents）配置模型。
 * <p>
 * 声明生成的 Word 文档是否启用目录（enabled）、目录包含的最大标题级别（maxLevel：默认 1-3 级），
 * 以及是否在 Word 软件打开文档时自动弹出更新目录提示（updateOnOpen，通过向 settings.xml 写入 w:updateFields 实现）。
 * </p>
 */
public class WordTocDefinition {

    /** 是否生成并包含目录。 */
    private boolean enabled;

    /** 目录显示的最大标题级别（默认 3）。 */
    private int maxLevel = 3;

    /** 是否在打开文档时由 Word 自动刷新目录域（默认 false）。 */
    private boolean updateOnOpen;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public boolean isUpdateOnOpen() {
        return updateOnOpen;
    }

    public void setUpdateOnOpen(boolean updateOnOpen) {
        this.updateOnOpen = updateOnOpen;
    }
}
