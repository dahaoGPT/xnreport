package com.xn.report.config.definition;

import com.xn.report.dataset.DatasetType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatasetDefinition {

    private String id;
    private String sheetName;
    private String sqlFile;
    private String sql;
    private DatasetType resultType;
    private List<String> dependsOn = new ArrayList<String>();
    private Map<String, ParameterBindingDefinition> parameters =
            new LinkedHashMap<String, ParameterBindingDefinition>();
    private Map<String, FieldDefinition> expectedFields =
            new LinkedHashMap<String, FieldDefinition>();
    private Integer timeoutSeconds;
    private Integer maxRows;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getSqlFile() {
        return sqlFile;
    }

    public void setSqlFile(String sqlFile) {
        this.sqlFile = sqlFile;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public DatasetType getResultType() {
        return resultType;
    }

    public void setResultType(DatasetType resultType) {
        this.resultType = resultType;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn == null ? new ArrayList<String>() : dependsOn;
    }

    public Map<String, ParameterBindingDefinition> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ParameterBindingDefinition> parameters) {
        this.parameters = parameters == null
                ? new LinkedHashMap<String, ParameterBindingDefinition>() : parameters;
    }

    public Map<String, FieldDefinition> getExpectedFields() {
        return expectedFields;
    }

    public void setExpectedFields(Map<String, FieldDefinition> expectedFields) {
        this.expectedFields = expectedFields == null
                ? new LinkedHashMap<String, FieldDefinition>() : expectedFields;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }
}
