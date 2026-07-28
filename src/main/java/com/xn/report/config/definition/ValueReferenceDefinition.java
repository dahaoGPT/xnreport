package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ValueReferenceDefinition {

    public enum Source {
        LITERAL,
        CURRENT_FIELD,
        DATASET_FIELD,
        RUNTIME_PARAMETER
    }

    private Source source;
    private Object value;
    private String dataset;
    private String field;
    private String parameter;
    @JsonIgnore
    private final Set<String> presentProperties = new LinkedHashSet<String>();

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        mark("source");
        this.source = source;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        mark("value");
        this.value = value;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        mark("dataset");
        this.dataset = dataset;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        mark("field");
        this.field = field;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        mark("parameter");
        this.parameter = parameter;
    }

    @JsonIgnore
    public boolean hasProperty(String property) {
        return presentProperties.contains(property);
    }

    @JsonIgnore
    public Set<String> getPresentProperties() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(presentProperties));
    }

    private void mark(String property) {
        presentProperties.add(property);
    }
}
