package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WordNumberingDefinition {

    private Long numId;
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
