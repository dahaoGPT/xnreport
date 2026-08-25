package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

/**
 * Word 正文章节结构树定义模型。
 * <p>
 * 声明 Word 报表正文中的单个章节节点：
 * <ul>
 *   <li><b>章节元数据</b>：章节唯一 ID（id）、标题（title）、大纲级别（level：1 到 4）。</li>
 *   <li><b>内容组件（components）</b>：挂载在当前章节下的内容组件列表（{@link WordComponentDefinition}）。</li>
 *   <li><b>子章节递归（children）</b>：嵌套的子章节列表，形成严谨的树状大纲结构。</li>
 *   <li><b>空数据处理</b>：章节空数据策略（emptyStrategy）与提示文案（emptyMessage）。</li>
 * </ul>
 * </p>
 */
public class WordSectionDefinition {

    /** 章节唯一标识。 */
    private String id;

    /** 章节标题文本。 */
    private String title;

    /** 章节大纲级别（1 对应 Heading 1，2 对应 Heading 2，依此类推）。 */
    private int level;

    /** 章节内全部组件为空时的处理策略（KEEP, SHOW_EMPTY, SKIP）。 */
    private String emptyStrategy;

    /** 章节空数据时展示的提示文案。 */
    private String emptyMessage = "暂无数据";

    /** 当前章节包含的内容组件列表。 */
    private List<WordComponentDefinition> components =
            new ArrayList<WordComponentDefinition>();

    /** 子章节列表（嵌套递归）。 */
    private List<WordSectionDefinition> children = new ArrayList<WordSectionDefinition>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getEmptyStrategy() {
        return emptyStrategy;
    }

    public void setEmptyStrategy(String emptyStrategy) {
        this.emptyStrategy = emptyStrategy;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    public List<WordComponentDefinition> getComponents() {
        return components;
    }

    public void setComponents(List<WordComponentDefinition> components) {
        this.components = components == null
                ? new ArrayList<WordComponentDefinition>() : components;
    }

    public List<WordSectionDefinition> getChildren() {
        return children;
    }

    public void setChildren(List<WordSectionDefinition> children) {
        this.children = children == null
                ? new ArrayList<WordSectionDefinition>() : children;
    }
}
