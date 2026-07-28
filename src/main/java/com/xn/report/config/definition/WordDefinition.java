package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

public class WordDefinition {

    private WordCoverDefinition cover = new WordCoverDefinition();
    private WordTocDefinition toc = new WordTocDefinition();
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

    public List<WordSectionDefinition> getSections() {
        return sections;
    }

    public void setSections(List<WordSectionDefinition> sections) {
        this.sections = sections == null
                ? new ArrayList<WordSectionDefinition>() : sections;
    }
}
