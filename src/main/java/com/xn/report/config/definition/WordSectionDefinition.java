package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

public class WordSectionDefinition {

    private String id;
    private String title;
    private int level;
    private String emptyStrategy;
    private String emptyMessage = "暂无数据";
    private List<WordComponentDefinition> components =
            new ArrayList<WordComponentDefinition>();
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
