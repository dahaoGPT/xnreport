package com.xn.report.sql;

import com.xn.report.dataset.DatasetRow;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ResultSetRowMapper {

    public DatasetRow map(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new IllegalArgumentException("ResultSet must not be null");
        }
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        Map<String, String> normalizedLabels = new LinkedHashMap<String, String>();
        for (int index = 1; index <= columnCount; index++) {
            String label = requireLabel(metadata.getColumnLabel(index), index);
            String normalized = label.toLowerCase(Locale.ROOT);
            if (normalizedLabels.containsKey(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate column label ignoring case: " + label);
            }
            normalizedLabels.put(normalized, label);
            values.put(label, normalize(resultSet.getObject(index)));
        }
        return toRow(values);
    }

    private static DatasetRow toRow(Map<String, Object> values) {
        Object[] pairs = new Object[values.size() * 2];
        int index = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            pairs[index++] = entry.getKey();
            pairs[index++] = entry.getValue();
        }
        return DatasetRow.of(pairs);
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof Time) {
            return ((Time) value).toLocalTime();
        }
        if (value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Float || value instanceof Double) {
            return new BigDecimal(String.valueOf(value));
        }
        if (value instanceof BigInteger) {
            BigInteger integer = (BigInteger) value;
            if (integer.bitLength() < Long.SIZE) {
                return Long.valueOf(integer.longValue());
            }
            return integer;
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return Long.valueOf(((Number) value).longValue());
        }
        if (value instanceof byte[]) {
            return ((byte[]) value).clone();
        }
        return value;
    }

    private static String requireLabel(String label, int index) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "JDBC column label must not be blank at index " + index);
        }
        return label;
    }
}
