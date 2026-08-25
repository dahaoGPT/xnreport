package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Word 多级大纲编号定义模型。
 * <p>
 * 声明 1 到 4 级章节标题的自动编号格式（如 1 级："一、"、2 级："1.1"、3 级："（1）"、4 级："①"），
 * 确保生成的 Word 文档具备专业标准的多级大纲编号。
 * </p>
 */
public class WordNumberingDefinition {

    /** 模板中绑定的底层 Word AbstractNum / Num 编号定义 ID。 */
    private Long numId;

    /** 各级别编号格式定义列表（默认提供 1-4 级标准中文大纲定义）。 */
    private List<WordNumberingLevelDefinition> levels = defaultLevels();

    public Long getNumId() {
        return numId;
    }

    public void setNumId(Long numId) {
        this.numId = numId;
    }

    public List<WordNumberingLevelDefinition> getLevels() {
        return levels;
    }

    public void setLevels(List<WordNumberingLevelDefinition> levels) {
        this.levels = levels == null
                ? new ArrayList<WordNumberingLevelDefinition>() : levels;
    }

    /**
     * 构建默认的 1-4 级大纲编号级别列表。
     */
    private static List<WordNumberingLevelDefinition> defaultLevels() {
        return new ArrayList<WordNumberingLevelDefinition>(Arrays.asList(
                level(1, "chineseCounting", "%1、"),
                level(2, "decimal", "%1.%2"),
                level(3, "decimal", "（%3）"),
                level(4, "decimalEnclosedCircle", "%4")));
    }

    private static WordNumberingLevelDefinition level(
            int level, String numFmt, String lvlText) {
        WordNumberingLevelDefinition definition =
                new WordNumberingLevelDefinition();
        definition.setLevel(level);
        definition.setNumFmt(numFmt);
        definition.setLvlText(lvlText);
        return definition;
    }
}
