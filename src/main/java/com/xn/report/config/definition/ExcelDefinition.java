package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 工作簿渲染与数据绑定配置模型。
 * <p>
 * 对应配置文件中的 {@code excel} 节点，包含单值单元格绑定（{@link ExcelValueBinding}）
 * 与多行表格数据写入绑定（{@link ExcelTableBinding}）。
 * </p>
 */
public class ExcelDefinition {

    /** 单元格标量值绑定列表。 */
    private List<ExcelValueBinding> valueBindings =
            new ArrayList<ExcelValueBinding>();

    /** 反序列化时 valueBindings 是否显式配置为 null。 */
    @JsonIgnore
    private boolean valueBindingsExplicitNull;

    /** 多行表格数据展开绑定列表。 */
    private List<ExcelTableBinding> tableBindings =
            new ArrayList<ExcelTableBinding>();

    /** 反序列化时 tableBindings 是否显式配置为 null。 */
    @JsonIgnore
    private boolean tableBindingsExplicitNull;

    public List<ExcelValueBinding> getValueBindings() {
        return valueBindings;
    }

    public void setValueBindings(List<ExcelValueBinding> valueBindings) {
        this.valueBindingsExplicitNull = valueBindings == null;
        this.valueBindings = valueBindings == null
                ? new ArrayList<ExcelValueBinding>() : valueBindings;
    }

    @JsonIgnore
    public boolean isValueBindingsExplicitNull() {
        return valueBindingsExplicitNull;
    }

    public List<ExcelTableBinding> getTableBindings() {
        return tableBindings;
    }

    public void setTableBindings(List<ExcelTableBinding> tableBindings) {
        this.tableBindingsExplicitNull = tableBindings == null;
        this.tableBindings = tableBindings == null
                ? new ArrayList<ExcelTableBinding>() : tableBindings;
    }

    @JsonIgnore
    public boolean isTableBindingsExplicitNull() {
        return tableBindingsExplicitNull;
    }
}
