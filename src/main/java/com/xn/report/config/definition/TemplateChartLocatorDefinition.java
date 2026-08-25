package com.xn.report.config.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 分组原生模板图表定位器配置定义模型。
 * <p>
 * 当按 groupByField（如研发中心）分组渲染多个原生图表时，
 * 用于将特定分组标识（groupKey）与模板中对应的图表标记（marker）或图表索引（index）进行精准映射匹配。
 * </p>
 */
public final class TemplateChartLocatorDefinition {

    /** 匹配的分组业务键值（如 "开发一中心"）。 */
    private String groupKey;

    /** 模板中该图表的标记文本（如 "[CHART:center_1]"）。 */
    private String marker;

    /** 模板中该图表的序号索引（0-based）。 */
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
