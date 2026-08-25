package com.xn.report.config.definition;

import java.util.ArrayList;
import java.util.List;

/**
 * Word 模板表格绑定配置模型。
 * <p>
 * 声明如何将数据集数据填充到 Word 模板中预设的表格或段落锚点中：
 * <ul>
 *   <li><b>定位方式</b>：通过段落标记文本（marker）或表格标识（tableId）。</li>
 *   <li><b>扩行策略（strategy）</b>：PROTOTYPE（克隆原型行样式并向下扩充数据行）。</li>
 *   <li><b>列映射列表（columns）</b>：字段映射与格式化样式（{@link WordTableColumnDefinition}）。</li>
 * </ul>
 * </p>
 */
public class WordTableBinding {

    /** 表格绑定唯一标识。 */
    private String id;

    /** 绑定的数据源数据集 ID。 */
    private String dataset;

    /** 模板中匹配该表格的文本标记（如 "[TABLE:center_summary]"）。 */
    private String marker;

    /** 模板中表格的唯一标识。 */
    private String tableId;

    /** 数据行渲染策略（默认为 PROTOTYPE 原型克隆）。 */
    private String strategy = "PROTOTYPE";

    /** 表格列定义列表。 */
    private List<WordTableColumnDefinition> columns =
            new ArrayList<WordTableColumnDefinition>();

    /** 空数据处理策略（KEEP, SHOW_EMPTY, SKIP）。 */
    private String emptyStrategy = "SHOW_EMPTY";

    /** 空数据时显示的提示文案。 */
    private String emptyMessage = "暂无数据";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<WordTableColumnDefinition> getColumns() {
        return columns;
    }

    public void setColumns(List<WordTableColumnDefinition> columns) {
        this.columns = columns == null
                ? new ArrayList<WordTableColumnDefinition>() : columns;
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
}
