package com.xn.report.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetSchema;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ResultSetRowMapperTest {

    @Test
    void buildsOrderedSchemaFromJdbcMetadataWithoutReadingARow() throws Exception {
        ResultSetMetaData metadata = metadata(
                new String[] {
                    "id", "amount", "ratio", "businessDate", "createdAt",
                    "enabled", "title", "payload"
                },
                new int[] {
                    Types.BIGINT, Types.NUMERIC, Types.FLOAT, Types.DATE,
                    Types.TIMESTAMP, Types.BIT, Types.NVARCHAR, Types.BLOB
                });

        DatasetSchema schema = new ResultSetRowMapper().schema(metadata);

        assertThat(schema.fieldNames()).containsExactly(
                "id", "amount", "ratio", "businessDate", "createdAt",
                "enabled", "title", "payload");
        assertThat(schema.typeOf("id")).isEqualTo(Long.class);
        assertThat(schema.typeOf("amount")).isEqualTo(BigDecimal.class);
        assertThat(schema.typeOf("ratio")).isEqualTo(BigDecimal.class);
        assertThat(schema.typeOf("businessDate")).isEqualTo(LocalDate.class);
        assertThat(schema.typeOf("createdAt")).isEqualTo(LocalDateTime.class);
        assertThat(schema.typeOf("enabled")).isEqualTo(Boolean.class);
        assertThat(schema.typeOf("title")).isEqualTo(String.class);
        assertThat(schema.typeOf("payload")).isEqualTo(byte[].class);
    }

    @Test
    void metadataRejectsCaseInsensitiveDuplicateAliasesEvenWithNoRows()
            throws Exception {
        ResultSetMetaData metadata = metadata(
                new String[] {"name", "NAME"},
                new int[] {Types.VARCHAR, Types.VARCHAR});

        assertThatThrownBy(() -> new ResultSetRowMapper().schema(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAME");
    }

    @Test
    void mapsColumnLabelsInDeclarationOrderAndNormalizesJdbcValues() throws Exception {
        ResultSet resultSet = resultSet(
                new String[] {
                    "tinyValue", "wholeValue", "largeValue", "avgHours",
                    "ratio", "statDate", "statTime", "createdAt", "enabled",
                    "title", "payload", "note"
                },
                new int[] {
                    Types.TINYINT, Types.INTEGER, Types.BIGINT, Types.DECIMAL,
                    Types.DOUBLE, Types.DATE, Types.TIME, Types.TIMESTAMP,
                    Types.BOOLEAN, Types.VARCHAR, Types.VARBINARY, Types.VARCHAR
                },
                new Object[] {
                    Integer.valueOf(7),
                    Long.valueOf(42L),
                    new BigInteger("9223372036854775807"),
                    new BigInteger("25"),
                    Double.valueOf(0.1d),
                    java.sql.Date.valueOf("2026-07-28"),
                    Time.valueOf("09:30:15"),
                    Timestamp.valueOf("2026-07-28 09:30:15.123456789"),
                    Integer.valueOf(1),
                    Character.valueOf('A'),
                    new byte[] {1, 2},
                    null
                });

        DatasetRow row = new ResultSetRowMapper().map(resultSet);

        assertThat(row.fieldNames()).containsExactly(
                "tinyValue", "wholeValue", "largeValue", "avgHours",
                "ratio", "statDate", "statTime", "createdAt", "enabled",
                "title", "payload", "note");
        assertThat(row.get("tinyValue")).isEqualTo(7L);
        assertThat(row.get("wholeValue")).isEqualTo(42L);
        assertThat(row.get("largeValue")).isEqualTo(Long.MAX_VALUE);
        assertThat(row.get("avgHours")).isEqualTo(new BigDecimal("25"));
        assertThat(row.get("ratio")).isEqualTo(new BigDecimal("0.1"));
        assertThat(row.get("statDate")).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(row.get("statTime")).isEqualTo(LocalTime.of(9, 30, 15));
        assertThat(row.get("createdAt"))
                .isEqualTo(LocalDateTime.of(2026, 7, 28, 9, 30, 15, 123456789));
        assertThat(row.get("enabled")).isEqualTo(Boolean.TRUE);
        assertThat(row.get("title")).isEqualTo("A");
        assertThat((byte[]) row.get("payload")).containsExactly(1, 2);
        assertThat(row.get("note")).isNull();
    }

    @Test
    void rejectsJdbcIntegersOutsideTheLongRange() throws Exception {
        ResultSet resultSet = resultSet(
                new String[] {"unsignedValue"},
                new int[] {Types.BIGINT},
                new Object[] {new BigInteger("9223372036854775808")});

        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsignedValue")
                .hasMessageContaining("Long");
    }

    @Test
    void convertsDriverSpecificBitRepresentationsToBoolean() throws Exception {
        DatasetRow row = new ResultSetRowMapper().map(resultSet(
                new String[] {"zeroBits", "oneBits", "zeroNumber", "oneNumber"},
                new int[] {Types.BIT, Types.BIT, Types.BOOLEAN, Types.BOOLEAN},
                new Object[] {
                    new byte[] {0, 0}, new byte[] {0, 1},
                    Integer.valueOf(0), Long.valueOf(2)
                }));

        assertThat(row.get("zeroBits")).isEqualTo(Boolean.FALSE);
        assertThat(row.get("oneBits")).isEqualTo(Boolean.TRUE);
        assertThat(row.get("zeroNumber")).isEqualTo(Boolean.FALSE);
        assertThat(row.get("oneNumber")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void convertsJdbcClobToStringAndReleasesIt() throws Exception {
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1L, 5)).thenReturn("hello");

        DatasetRow row = new ResultSetRowMapper().map(resultSet(
                new String[] {"description"},
                new int[] {Types.CLOB},
                new Object[] {clob}));

        assertThat(row.get("description")).isEqualTo("hello");
        verify(clob).free();
    }

    @Test
    void releasesClobAndBlobWhenLengthFails() throws Exception {
        Clob clob = mock(Clob.class);
        Blob blob = mock(Blob.class);
        when(clob.length()).thenThrow(new SQLException("clob length failed"));
        when(blob.length()).thenThrow(new SQLException("blob length failed"));

        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet(
                new String[] {"description"},
                new int[] {Types.CLOB},
                new Object[] {clob})))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("clob length failed");
        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet(
                new String[] {"payload"},
                new int[] {Types.BLOB},
                new Object[] {blob})))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("blob length failed");

        verify(clob).free();
        verify(blob).free();
    }

    @Test
    void releasesClobAndBlobWhenReadFails() throws Exception {
        Clob clob = mock(Clob.class);
        Blob blob = mock(Blob.class);
        when(clob.length()).thenReturn(3L);
        when(clob.getSubString(1L, 3))
                .thenThrow(new SQLException("clob read failed"));
        when(blob.length()).thenReturn(3L);
        when(blob.getBytes(1L, 3))
                .thenThrow(new SQLException("blob read failed"));

        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet(
                new String[] {"description"},
                new int[] {Types.CLOB},
                new Object[] {clob})))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("clob read failed");
        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet(
                new String[] {"payload"},
                new int[] {Types.BLOB},
                new Object[] {blob})))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("blob read failed");

        verify(clob).free();
        verify(blob).free();
    }

    @Test
    void releasesClobAndBlobWhenLengthExceedsSupportedLimit()
            throws Exception {
        Clob clob = mock(Clob.class);
        Blob blob = mock(Blob.class);
        long oversized = (long) Integer.MAX_VALUE + 1L;
        when(clob.length()).thenReturn(oversized);
        when(blob.length()).thenReturn(oversized);

        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet(
                new String[] {"description"},
                new int[] {Types.CLOB},
                new Object[] {clob})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
        assertThatThrownBy(() -> new ResultSetRowMapper().map(resultSet(
                new String[] {"payload"},
                new int[] {Types.BLOB},
                new Object[] {blob})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");

        verify(clob).free();
        verify(blob).free();
    }

    @Test
    void copiesBinaryValuesDefensively() throws Exception {
        byte[] source = new byte[] {1, 2};
        DatasetRow row = new ResultSetRowMapper().map(
                resultSet(
                        new String[] {"payload"},
                        new int[] {Types.LONGVARBINARY},
                        new Object[] {source}));

        source[0] = 9;
        byte[] firstRead = (byte[]) row.get("payload");
        firstRead[1] = 8;

        assertThat((byte[]) row.get("payload")).containsExactly(1, 2);
    }

    @Test
    void rejectsBlankOrCaseInsensitiveDuplicateLabels() throws Exception {
        assertThatThrownBy(() -> new ResultSetRowMapper().map(
                resultSet(
                        new String[] {"name", "NAME"},
                        new int[] {Types.VARCHAR, Types.VARCHAR},
                        new Object[] {"A", "B"})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAME");
        assertThatThrownBy(() -> new ResultSetRowMapper().map(
                resultSet(
                        new String[] {" "},
                        new int[] {Types.VARCHAR},
                        new Object[] {"A"})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    private static ResultSet resultSet(
            String[] labels, int[] columnTypes, Object[] values) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = metadata(labels, columnTypes);
        when(resultSet.getMetaData()).thenReturn(metadata);
        for (int index = 0; index < labels.length; index++) {
            when(resultSet.getObject(index + 1)).thenReturn(values[index]);
        }
        return resultSet;
    }

    private static ResultSetMetaData metadata(
            String[] labels, int[] columnTypes) throws Exception {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(labels.length);
        for (int index = 0; index < labels.length; index++) {
            when(metadata.getColumnLabel(index + 1)).thenReturn(labels[index]);
            when(metadata.getColumnType(index + 1)).thenReturn(columnTypes[index]);
        }
        return metadata;
    }
}
