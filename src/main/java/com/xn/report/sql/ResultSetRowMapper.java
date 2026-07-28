package com.xn.report.sql;

import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ResultSetRowMapper {

    public static final int DEFAULT_MAX_LOB_CHARS = 16 * 1024 * 1024;
    public static final int DEFAULT_MAX_LOB_BYTES = 16 * 1024 * 1024;
    private static final int LOB_BUFFER_SIZE = 8 * 1024;

    private final int maxLobChars;
    private final int maxLobBytes;

    public ResultSetRowMapper() {
        this(DEFAULT_MAX_LOB_CHARS, DEFAULT_MAX_LOB_BYTES);
    }

    public ResultSetRowMapper(int maxLobChars, int maxLobBytes) {
        this.maxLobChars = requirePositive("maxLobChars", maxLobChars);
        this.maxLobBytes = requirePositive("maxLobBytes", maxLobBytes);
    }

    public DatasetSchema schema(ResultSetMetaData metadata) throws SQLException {
        if (metadata == null) {
            throw new IllegalArgumentException("ResultSetMetaData must not be null");
        }
        int columnCount = metadata.getColumnCount();
        Object[] pairs = new Object[columnCount * 2];
        Map<String, String> normalizedLabels = new LinkedHashMap<String, String>();
        for (int index = 1; index <= columnCount; index++) {
            String label = uniqueLabel(metadata, index, normalizedLabels);
            pairs[(index - 1) * 2] = label;
            pairs[(index - 1) * 2 + 1] =
                    normalizedClass(metadata.getColumnType(index));
        }
        return DatasetSchema.of(pairs);
    }

    public DatasetRow map(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new IllegalArgumentException("ResultSet must not be null");
        }
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        Map<String, String> normalizedLabels = new LinkedHashMap<String, String>();
        for (int index = 1; index <= columnCount; index++) {
            String label = uniqueLabel(metadata, index, normalizedLabels);
            int columnType = metadata.getColumnType(index);
            values.put(label, readColumn(
                    resultSet, index, label, columnType));
        }
        return toRow(values);
    }

    private Object readColumn(
            ResultSet resultSet,
            int index,
            String label,
            int columnType) throws SQLException {
        if (isLargeCharacter(columnType)) {
            return readCharacterStream(
                    label, resultSet.getCharacterStream(index));
        }
        if (isLargeBinary(columnType)) {
            return readBinaryStream(label, resultSet.getBinaryStream(index));
        }
        return normalize(label, columnType, resultSet.getObject(index));
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

    private Object normalize(
            String label, int columnType, Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (isInteger(columnType)) {
            return normalizeInteger(label, value);
        }
        if (columnType == Types.DECIMAL || columnType == Types.NUMERIC) {
            return value instanceof BigDecimal
                    ? value : decimal(label, value);
        }
        if (columnType == Types.FLOAT
                || columnType == Types.REAL
                || columnType == Types.DOUBLE) {
            return decimal(label, value);
        }
        if (columnType == Types.DATE) {
            if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value).toLocalDate();
            }
            if (value instanceof LocalDate) {
                return value;
            }
            throw incompatible(label, columnType, value);
        }
        if (columnType == Types.TIME) {
            if (value instanceof Time) {
                return ((Time) value).toLocalTime();
            }
            if (value instanceof LocalTime) {
                return value;
            }
            throw incompatible(label, columnType, value);
        }
        if (columnType == Types.TIMESTAMP
                || columnType == Types.TIMESTAMP_WITH_TIMEZONE) {
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            }
            if (value instanceof LocalDateTime) {
                return value;
            }
            throw incompatible(label, columnType, value);
        }
        if (columnType == Types.BIT || columnType == Types.BOOLEAN) {
            return normalizeBoolean(label, columnType, value);
        }
        if (isCharacter(columnType)) {
            return checkCharacterLength(label, String.valueOf(value));
        }
        if (isBinary(columnType)) {
            return normalizeBinary(label, columnType, value);
        }
        if (value instanceof byte[]) {
            return copyBinary(label, (byte[]) value);
        }
        return value;
    }

    private static Long normalizeInteger(String label, Object value) {
        try {
            if (value instanceof BigInteger) {
                return Long.valueOf(((BigInteger) value).longValueExact());
            }
            if (value instanceof BigDecimal) {
                return Long.valueOf(((BigDecimal) value).longValueExact());
            }
            if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                return Long.valueOf(((Number) value).longValue());
            }
            if (value instanceof Number) {
                return Long.valueOf(
                        new BigDecimal(String.valueOf(value)).longValueExact());
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "JDBC integer column " + label
                            + " is outside the Long range: " + value,
                    exception);
        }
        throw incompatible(label, Types.BIGINT, value);
    }

    private static BigDecimal decimal(String label, Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "JDBC decimal column " + label
                            + " cannot be represented as BigDecimal",
                    exception);
        }
    }

    private static Boolean normalizeBoolean(
            String label, int columnType, Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof byte[]) {
            for (byte current : (byte[]) value) {
                if (current != 0) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value))
                    .compareTo(BigDecimal.ZERO) != 0;
        }
        throw incompatible(label, columnType, value);
    }

    private byte[] normalizeBinary(
            String label, int columnType, Object value) throws SQLException {
        if (value instanceof byte[]) {
            return copyBinary(label, (byte[]) value);
        }
        throw incompatible(label, columnType, value);
    }

    private String readCharacterStream(String label, Reader reader)
            throws SQLException {
        if (reader == null) {
            return null;
        }
        try (Reader source = reader) {
            StringBuilder value = new StringBuilder(
                    Math.min(maxLobChars, LOB_BUFFER_SIZE));
            char[] buffer = new char[LOB_BUFFER_SIZE];
            int total = 0;
            int count;
            while ((count = source.read(buffer, 0, buffer.length)) != -1) {
                if (count > maxLobChars - total) {
                    throw lobTooLarge(label, "character", maxLobChars);
                }
                value.append(buffer, 0, count);
                total += count;
            }
            return value.toString();
        } catch (IOException exception) {
            throw new SQLException(
                    "Failed to read JDBC character column " + label,
                    exception);
        }
    }

    private byte[] readBinaryStream(String label, InputStream stream)
            throws SQLException {
        if (stream == null) {
            return null;
        }
        try (InputStream source = stream) {
            ByteArrayOutputStream value = new ByteArrayOutputStream(
                    Math.min(maxLobBytes, LOB_BUFFER_SIZE));
            byte[] buffer = new byte[LOB_BUFFER_SIZE];
            int total = 0;
            int count;
            while ((count = source.read(buffer, 0, buffer.length)) != -1) {
                if (count > maxLobBytes - total) {
                    throw lobTooLarge(label, "binary", maxLobBytes);
                }
                value.write(buffer, 0, count);
                total += count;
            }
            return value.toByteArray();
        } catch (IOException exception) {
            throw new SQLException(
                    "Failed to read JDBC binary column " + label,
                    exception);
        }
    }

    private String checkCharacterLength(String label, String value) {
        if (value.length() > maxLobChars) {
            throw lobTooLarge(label, "character", maxLobChars);
        }
        return value;
    }

    private byte[] copyBinary(String label, byte[] value) {
        if (value.length > maxLobBytes) {
            throw lobTooLarge(label, "binary", maxLobBytes);
        }
        return value.clone();
    }

    private static IllegalArgumentException lobTooLarge(
            String label, String kind, int maximum) {
        return new IllegalArgumentException(
                "JDBC " + kind + " column " + label
                        + " exceeded configured limit " + maximum);
    }

    private static int requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static boolean isInteger(int columnType) {
        return columnType == Types.TINYINT
                || columnType == Types.SMALLINT
                || columnType == Types.INTEGER
                || columnType == Types.BIGINT;
    }

    private static boolean isCharacter(int columnType) {
        return columnType == Types.CHAR
                || columnType == Types.VARCHAR
                || columnType == Types.LONGVARCHAR
                || columnType == Types.NCHAR
                || columnType == Types.NVARCHAR
                || columnType == Types.LONGNVARCHAR
                || columnType == Types.CLOB
                || columnType == Types.NCLOB;
    }

    private static boolean isLargeCharacter(int columnType) {
        return columnType == Types.CLOB
                || columnType == Types.NCLOB
                || columnType == Types.LONGVARCHAR
                || columnType == Types.LONGNVARCHAR;
    }

    private static boolean isBinary(int columnType) {
        return columnType == Types.BINARY
                || columnType == Types.VARBINARY
                || columnType == Types.LONGVARBINARY
                || columnType == Types.BLOB;
    }

    private static boolean isLargeBinary(int columnType) {
        return columnType == Types.BLOB
                || columnType == Types.LONGVARBINARY;
    }

    private static Class<?> normalizedClass(int columnType) {
        if (isInteger(columnType)) {
            return Long.class;
        }
        if (columnType == Types.DECIMAL
                || columnType == Types.NUMERIC
                || columnType == Types.FLOAT
                || columnType == Types.REAL
                || columnType == Types.DOUBLE) {
            return BigDecimal.class;
        }
        if (columnType == Types.DATE) {
            return LocalDate.class;
        }
        if (columnType == Types.TIME) {
            return LocalTime.class;
        }
        if (columnType == Types.TIMESTAMP
                || columnType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return LocalDateTime.class;
        }
        if (columnType == Types.BIT || columnType == Types.BOOLEAN) {
            return Boolean.class;
        }
        if (isCharacter(columnType)) {
            return String.class;
        }
        if (isBinary(columnType)) {
            return byte[].class;
        }
        return Object.class;
    }

    private static String uniqueLabel(
            ResultSetMetaData metadata,
            int index,
            Map<String, String> normalizedLabels) throws SQLException {
        String label = requireLabel(metadata.getColumnLabel(index), index);
        String normalized = label.toLowerCase(Locale.ROOT);
        if (normalizedLabels.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "Duplicate column label ignoring case: " + label);
        }
        normalizedLabels.put(normalized, label);
        return label;
    }

    private static IllegalArgumentException incompatible(
            String label, int columnType, Object value) {
        return new IllegalArgumentException(
                "JDBC column " + label + " of type " + columnType
                        + " returned unsupported value "
                        + value.getClass().getName());
    }

    private static String requireLabel(String label, int index) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "JDBC column label must not be blank at index " + index);
        }
        return label;
    }
}
