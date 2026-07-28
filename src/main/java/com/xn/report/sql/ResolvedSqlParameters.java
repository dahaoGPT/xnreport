package com.xn.report.sql;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public final class ResolvedSqlParameters {

    private final Map<String, Object> values;

    public ResolvedSqlParameters(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        this.values = copyMap(values);
    }

    public Map<String, Object> asMap() {
        return copyMap(values);
    }

    public MapSqlParameterSource toMapSqlParameterSource() {
        return new MapSqlParameterSource(asMap());
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Timestamp) {
            Timestamp source = (Timestamp) value;
            Timestamp copy = new Timestamp(source.getTime());
            copy.setNanos(source.getNanos());
            return copy;
        }
        if (value instanceof java.sql.Date) {
            return new java.sql.Date(((java.sql.Date) value).getTime());
        }
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
        }
        if (value instanceof Collection<?>) {
            Collection<?> source = (Collection<?>) value;
            List<Object> copy = new ArrayList<Object>(source.size());
            for (Object element : source) {
                copy.add(copyValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
