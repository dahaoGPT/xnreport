package com.xn.report.config.definition;

public class PolicyDefinition {

    private String emptyData;
    private String emptyMessage;
    private String missingField;
    private String typeMismatch;
    private String nullValue;
    private String emptyCollectionParameter;
    private String unresolvedPlaceholder;

    public String getEmptyData() {
        return emptyData;
    }

    public void setEmptyData(String emptyData) {
        this.emptyData = emptyData;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    public String getMissingField() {
        return missingField;
    }

    public void setMissingField(String missingField) {
        this.missingField = missingField;
    }

    public String getTypeMismatch() {
        return typeMismatch;
    }

    public void setTypeMismatch(String typeMismatch) {
        this.typeMismatch = typeMismatch;
    }

    public String getNullValue() {
        return nullValue;
    }

    public void setNullValue(String nullValue) {
        this.nullValue = nullValue;
    }

    public String getEmptyCollectionParameter() {
        return emptyCollectionParameter;
    }

    public void setEmptyCollectionParameter(String emptyCollectionParameter) {
        this.emptyCollectionParameter = emptyCollectionParameter;
    }

    public String getUnresolvedPlaceholder() {
        return unresolvedPlaceholder;
    }

    public void setUnresolvedPlaceholder(String unresolvedPlaceholder) {
        this.unresolvedPlaceholder = unresolvedPlaceholder;
    }
}
