package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

/**
 * Word 章节正文组件定义模型。
 * <p>
 * 声明 Word 章节下嵌入的具体内容组件：
 * <ul>
 *   <li><b>组件类型（type）</b>：SCENARIO（场景说明）、KEY_FACTORS（关键因素）、FIXED_TEXT（固定文本）、RULE_TEXT（规则文字）、CHART（图表）、TABLE（数据表格）、UNIT（责任单位）、ATTACHMENT（附件列表）。</li>
 *   <li><b>组件关联属性</b>：文本内容（text）、关联图表（chartId）、关联文字分析（narrativeId）、表格定义（tableId / dataset / columns）、图片宽度（widthInches）与对齐方式（alignment）等。</li>
 * </ul>
 * </p>
 */
public class WordComponentDefinition {

    /** 组件类型。 */
    private String type;

    /** 纯文本或占位符内容。 */
    private String text;

    /** 关联的图表 ID。 */
    private String chartId;

    /** 关联的段落/叙述分析 ID。 */
    private String narrativeId;

    /** 关联的表格 ID。 */
    private String tableId;

    /** 绑定的数据集 ID。 */
    private String dataset;

    /** 内嵌表格的列定义列表。 */
    private List<WordTableColumnDefinition> columns =
            new ArrayList<WordTableColumnDefinition>();

    /** 无数据时显示的提示文案。 */
    private String emptyMessage = "暂无数据";

    /** 图片插入宽度（英寸）。 */
    private Double widthInches;

    /** 图片在段落中的水平对齐方式（LEFT, CENTER, RIGHT）。 */
    private String alignment = WordImageAlignment.CENTER.name();

    /** 图表/表格的标题图注（Caption）。 */
    private String caption;

    /** 图片的替代文本描述（Alt Text）。 */
    private String altText;

    /** 标题。 */
    private String title;

    /** 描述信息。 */
    private String description;

    /** 列表项集合（如责任单位、关键因素条目）。 */
    private List<String> items = new ArrayList<String>();

    /** 组件级别异常降级策略。 */
    private PolicyDefinition policies = new PolicyDefinition();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getChartId() {
        return chartId;
    }

    public void setChartId(String chartId) {
        this.chartId = chartId;
    }

    public String getNarrativeId() {
        return narrativeId;
    }

    public void setNarrativeId(String narrativeId) {
        this.narrativeId = narrativeId;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public List<WordTableColumnDefinition> getColumns() {
        return columns;
    }

    public void setColumns(List<WordTableColumnDefinition> columns) {
        this.columns = columns == null
                ? new ArrayList<WordTableColumnDefinition>() : columns;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    public Double getWidthInches() {
        return widthInches;
    }

    public void setWidthInches(Double widthInches) {
        this.widthInches = widthInches;
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items == null ? new ArrayList<String>() : items;
    }

    public PolicyDefinition getPolicies() {
        return policies;
    }

    public void setPolicies(PolicyDefinition policies) {
        this.policies = policies == null ? new PolicyDefinition() : policies;
    }
}
