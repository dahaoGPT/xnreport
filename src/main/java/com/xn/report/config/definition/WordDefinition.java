package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

public class WordDefinition {

    private WordCoverDefinition cover = new WordCoverDefinition();
    private WordTocDefinition toc = new WordTocDefinition();
    private WordNumberingDefinition numbering = new WordNumberingDefinition();
    private List<WordTableBinding> tableBindings =
            new ArrayList<WordTableBinding>();
    private List<WordSectionDefinition> sections = new ArrayList<WordSectionDefinition>();

    public WordCoverDefinition getCover() {
        return cover;
    }

    public void setCover(WordCoverDefinition cover) {
        this.cover = cover == null ? new WordCoverDefinition() : cover;
    }

    public WordTocDefinition getToc() {
        return toc;
    }

    public void setToc(WordTocDefinition toc) {
        this.toc = toc == null ? new WordTocDefinition() : toc;
    }

    public WordNumberingDefinition getNumbering() {
        return numbering;
    }

    public void setNumbering(WordNumberingDefinition numbering) {
        this.numbering = numbering == null
                ? new WordNumberingDefinition() : numbering;
    }

    public List<WordSectionDefinition> getSections() {
        return sections;
    }

    public List<WordTableBinding> getTableBindings() {
        return tableBindings;
    }

    public void setTableBindings(List<WordTableBinding> tableBindings) {
        this.tableBindings = tableBindings == null
                ? new ArrayList<WordTableBinding>() : tableBindings;
    }

    public void setSections(List<WordSectionDefinition> sections) {
        this.sections = sections == null
                ? new ArrayList<WordSectionDefinition>() : sections;
    }
}
