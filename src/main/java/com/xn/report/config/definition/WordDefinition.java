package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档渲染总配置定义模型。
 * <p>
 * 对应主配置文件中的 {@code word} 节点，包含封面定义（{@link WordCoverDefinition}）、
 * 目录域（{@link WordTocDefinition}）、多级列表编号（{@link WordNumberingDefinition}）、
 * 模板全局表格绑定（{@link WordTableBinding}）以及正文层级章节树列表（{@link WordSectionDefinition}）。
 * </p>
 */
public class WordDefinition {

    /** 封面元数据绑定。 */
    private WordCoverDefinition cover = new WordCoverDefinition();

    /** 目录（TOC）更新与显示配置。 */
    private WordTocDefinition toc = new WordTocDefinition();

    /** 多级标题大纲编号规则定义。 */
    private WordNumberingDefinition numbering = new WordNumberingDefinition();

    /** 全局表格绑定列表。 */
    private List<WordTableBinding> tableBindings =
            new ArrayList<WordTableBinding>();

    /** 正文顶级章节列表。 */
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
