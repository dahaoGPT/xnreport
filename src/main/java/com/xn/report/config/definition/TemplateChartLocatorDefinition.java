package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Identifies the template chart assigned to one configured group.
 */
public final class TemplateChartLocatorDefinition {

    private String groupKey;
    private String marker;
    private Integer index;
    @JsonIgnore
    private final Set<String> presentProperties =
            new LinkedHashSet<String>();

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        mark("groupKey");
        this.groupKey = groupKey;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        mark("marker");
        this.marker = marker;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        mark("index");
        this.index = index;
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
