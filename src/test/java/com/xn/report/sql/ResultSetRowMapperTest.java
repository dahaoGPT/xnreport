package com.xn.report.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xn.report.dataset.DatasetRow;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ResultSetRowMapperTest {

    @Test
    void mapsColumnLabelsInDeclarationOrderAndNormalizesJdbcValues() throws Exception {
        ResultSet resultSet = resultSet(
                new String[] {
                    "tinyValue", "wholeValue", "largeValue", "avgHours",
                    "ratio", "statDate", "statTime", "createdAt", "payload", "note"
                },
                new Object[] {
                    Integer.valueOf(7),
                    Long.valueOf(42L),
                    new BigInteger("9223372036854775808"),
                    new BigDecimal("25.2700"),
                    Double.valueOf(0.1d),
                    java.sql.Date.valueOf("2026-07-28"),
                    Time.valueOf("09:30:15"),
                    Timestamp.valueOf("2026-07-28 09:30:15.123456789"),
                    new byte[] {1, 2},
                    null
                });

        DatasetRow row = new ResultSetRowMapper().map(resultSet);

        assertThat(row.fieldNames()).containsExactly(
                "tinyValue", "wholeValue", "largeValue", "avgHours",
                "ratio", "statDate", "statTime", "createdAt", "payload", "note");
        assertThat(row.get("tinyValue")).isEqualTo(7L);
        assertThat(row.get("wholeValue")).isEqualTo(42L);
        assertThat(row.get("largeValue"))
                .isEqualTo(new BigInteger("9223372036854775808"));
        assertThat(row.get("avgHours")).isEqualTo(new BigDecimal("25.2700"));
        assertThat(row.get("ratio")).isEqualTo(new BigDecimal("0.1"));
        assertThat(row.get("statDate")).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(row.get("statTime")).isEqualTo(LocalTime.of(9, 30, 15));
        assertThat(row.get("createdAt"))
                .isEqualTo(LocalDateTime.of(2026, 7, 28, 9, 30, 15, 123456789));
        assertThat((byte[]) row.get("payload")).containsExactly(1, 2);
        assertThat(row.get("note")).isNull();
    }

    @Test
    void copiesBinaryValuesDefensively() throws Exception {
        byte[] source = new byte[] {1, 2};
        DatasetRow row = new ResultSetRowMapper().map(
                resultSet(new String[] {"payload"}, new Object[] {source}));

        source[0] = 9;
        byte[] firstRead = (byte[]) row.get("payload");
        firstRead[1] = 8;

        assertThat((byte[]) row.get("payload")).containsExactly(1, 2);
    }

    @Test
    void rejectsBlankOrCaseInsensitiveDuplicateLabels() throws Exception {
        assertThatThrownBy(() -> new ResultSetRowMapper().map(
                resultSet(new String[] {"name", "NAME"}, new Object[] {"A", "B"})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAME");
        assertThatThrownBy(() -> new ResultSetRowMapper().map(
                resultSet(new String[] {" "}, new Object[] {"A"})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    private static ResultSet resultSet(String[] labels, Object[] values) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(labels.length);
        for (int index = 0; index < labels.length; index++) {
            when(metadata.getColumnLabel(index + 1)).thenReturn(labels[index]);
            when(resultSet.getObject(index + 1)).thenReturn(values[index]);
        }
        return resultSet;
    }
}
