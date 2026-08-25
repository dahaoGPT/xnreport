package com.xn.report.config.definition;

/**
 * 单个大纲级别的编号格式定义模型。
 * <p>
 * 定义特定级别（1-4）的数字格式（numFmt，如 decimal、chineseCounting、decimalEnclosedCircle）
 * 以及包含层级占位符的层级文本格式（lvlText，如 "%1."、"%1.%2"）。
 * </p>
 */
public class WordNumberingLevelDefinition {

    /** 大纲级别（1-4）。 */
    private int level;

    /** 编号数字格式（如 decimal, lowerLetter, upperLetter, lowerRoman, upperRoman, chineseCounting, decimalEnclosedCircle）。 */
    private String numFmt;

    /** 拼接格式化文本（如 "%1、"、"%1.%2"、"（%3）"）。 */
    private String lvlText;

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getNumFmt() {
        return numFmt;
    }

    public void setNumFmt(String numFmt) {
        this.numFmt = numFmt;
    }

    public String getLvlText() {
        return lvlText;
    }

    public void setLvlText(String lvlText) {
        this.lvlText = lvlText;
    }
}
