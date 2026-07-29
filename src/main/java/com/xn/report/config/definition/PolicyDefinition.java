package com.xn.report.config.definition;

import com.xn.report.policy.EmptyDataPolicy;
import com.xn.report.policy.MissingFieldPolicy;
import com.xn.report.policy.NullValuePolicy;
import com.xn.report.policy.TypeMismatchPolicy;

public class PolicyDefinition {

    private EmptyDataPolicy emptyData;
    private String emptyMessage;
    private MissingFieldPolicy missingField;
    private TypeMismatchPolicy typeMismatch;
    private NullValuePolicy nullValue;
    private String emptyCollectionParameter;
    private String unresolvedPlaceholder;

    public EmptyDataPolicy getEmptyData() {
        return emptyData;
    }

    public void setEmptyData(EmptyDataPolicy emptyData) {
        this.emptyData = emptyData;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    public MissingFieldPolicy getMissingField() {
        return missingField;
    }

    public void setMissingField(MissingFieldPolicy missingField) {
        this.missingField = missingField;
    }

    public TypeMismatchPolicy getTypeMismatch() {
        return typeMismatch;
    }

    public void setTypeMismatch(TypeMismatchPolicy typeMismatch) {
        this.typeMismatch = typeMismatch;
    }

    public NullValuePolicy getNullValue() {
        return nullValue;
    }

    public void setNullValue(NullValuePolicy nullValue) {
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

    public static PolicyDefinition systemDefaults() {
        PolicyDefinition definition = new PolicyDefinition();
        definition.setEmptyData(EmptyDataPolicy.OUTPUT_MESSAGE);
        definition.setMissingField(MissingFieldPolicy.FAIL);
        definition.setTypeMismatch(TypeMismatchPolicy.FAIL);
        definition.setNullValue(NullValuePolicy.RULE_NOT_MATCHED);
        definition.setEmptyCollectionParameter("FAIL");
        definition.setUnresolvedPlaceholder("FAIL");
        return definition;
    }

    public static PolicyDefinition component(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition rule(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition dataset(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition report(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition global(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    private static PolicyDefinition withEmptyData(EmptyDataPolicy policy) {
        PolicyDefinition definition = new PolicyDefinition();
        definition.setEmptyData(policy);
        return definition;
    }
}
