package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public class ExcelDefinition {

    private List<ExcelValueBinding> valueBindings =
            new ArrayList<ExcelValueBinding>();
    @JsonIgnore
    private boolean valueBindingsExplicitNull;
    private List<ExcelTableBinding> tableBindings =
            new ArrayList<ExcelTableBinding>();
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
