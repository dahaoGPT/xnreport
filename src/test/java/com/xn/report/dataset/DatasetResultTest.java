package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class DatasetResultTest {

    @Test
    void resolvesFieldsCaseInsensitivelyButKeepsOriginalOrder() {
        DatasetRow row = TestFixtures.row(
                "nodeName", "API设计",
                "avgHours", new BigDecimal("25.27"));

        assertThat(row.get("NODENAME")).isEqualTo("API设计");
        assertThat(row.getOrNull("AVGHOURS")).isEqualTo(new BigDecimal("25.27"));
        assertThat(row.containsField("nodename")).isTrue();
        assertThat(row.fieldNames()).containsExactly("nodeName", "avgHours");
    }

    @Test
    void rejectsFieldsThatDifferOnlyByCase() {
        assertThatThrownBy(() -> DatasetRow.of(
                "nodeName", "first",
                "NODENAME", "second"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODENAME");
    }

    @Test
    void rowIsAnImmutableCopyAndMissingRequiredFieldFailsClearly() {
        Map<String, Object> values = DatasetRow.of("name", "A").asMap();

        assertThatThrownBy(() -> values.put("name", "B"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DatasetRow.of("name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pairs");
        assertThatThrownBy(() -> DatasetRow.empty().get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void schemaDescribesOrderedFieldsAndTypesCaseInsensitively() {
        DatasetSchema schema = DatasetSchema.of(
                "nodeName", String.class,
                "avgHours", BigDecimal.class);

        assertThat(schema.fieldNames()).containsExactly("nodeName", "avgHours");
        assertThat(schema.typeOf("NODENAME")).isEqualTo(String.class);
        assertThat(schema.containsField("avghours")).isTrue();
        assertThatThrownBy(() -> schema.asMap().put("extra", Object.class))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DatasetSchema.of(
                "nodeName", String.class,
                "NODENAME", String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listCopiesRowsAndExposesItsShapeAndSchema() {
        List<DatasetRow> rows = new ArrayList<DatasetRow>();
        rows.add(TestFixtures.row("name", "A", "hours", new BigDecimal("8.00")));

        DatasetResult result = DatasetResult.list("people", rows);
        rows.clear();

        assertThat(result.id()).isEqualTo("people");
        assertThat(result.type()).isEqualTo(DatasetType.LIST);
        assertThat(result.list()).hasSize(1);
        assertThat(result.schema().fieldNames()).containsExactly("name", "hours");
        assertThat(result.schema().typeOf("hours")).isEqualTo(BigDecimal.class);
        assertThatThrownBy(() -> result.list().add(DatasetRow.empty()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void schemaTypeConflictNeverRecoversToAConcreteType() {
        DatasetResult result = DatasetResult.list("mixed", Arrays.asList(
                TestFixtures.row("value", "first"),
                TestFixtures.row("value", 2),
                TestFixtures.row("value", "third")));

        assertThat(result.schema().typeOf("value")).isEqualTo(Object.class);
    }

    @Test
    void deeplyCopiesJdbcMutableValuesAndArraysAcrossContextBoundaries() {
        byte[] bytes = new byte[]{1, 2};
        Date date = new Date(1_000L);
        Timestamp timestamp = new Timestamp(2_000L);
        timestamp.setNanos(123_456_789);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(3_000L);
        Object[] array = new Object[]{"fixed", new byte[]{4, 5}};
        DatasetRow row = TestFixtures.row(
                "bytes", bytes,
                "date", date,
                "timestamp", timestamp,
                "calendar", calendar,
                "array", array);
        DatasetContext context = DatasetContext.builder()
                .put(DatasetResult.list("mutable", Collections.singletonList(row)))
                .build();

        bytes[0] = 9;
        date.setTime(9_000L);
        timestamp.setTime(9_000L);
        calendar.setTimeInMillis(9_000L);
        ((byte[]) array[1])[0] = 9;
        array[0] = "changed";

        DatasetRow stored = context.get("mutable").list().get(0);
        assertThat((byte[]) stored.get("bytes")).containsExactly(1, 2);
        assertThat(((Date) stored.get("date")).getTime()).isEqualTo(1_000L);
        assertThat(((Timestamp) stored.get("timestamp")).getNanos())
                .isEqualTo(123_456_789);
        assertThat(((Calendar) stored.get("calendar")).getTimeInMillis())
                .isEqualTo(3_000L);
        assertThat((Object[]) stored.get("array"))
                .containsExactly("fixed", new byte[]{4, 5});

        byte[] returnedBytes = (byte[]) stored.get("bytes");
        returnedBytes[0] = 8;
        Date returnedDate = (Date) stored.asMap().get("date");
        returnedDate.setTime(8_000L);
        Object[] returnedArray = (Object[]) stored.asMap().get("array");
        ((byte[]) returnedArray[1])[0] = 8;

        assertThat((byte[]) stored.get("bytes")).containsExactly(1, 2);
        assertThat(((Date) stored.get("date")).getTime()).isEqualTo(1_000L);
        assertThat((Object[]) stored.get("array"))
                .containsExactly("fixed", new byte[]{4, 5});
    }

    @Test
    @SuppressWarnings("unchecked")
    void deeplyFreezesNestedCollectionsAndRejectsUnsupportedMutableValues() {
        byte[] nestedBytes = new byte[]{6, 7};
        Map<String, Object> nestedMap = new LinkedHashMap<String, Object>();
        nestedMap.put("bytes", nestedBytes);
        Set<Object> nestedSet = new LinkedHashSet<Object>();
        nestedSet.add(new Date(4_000L));
        List<Object> source = new ArrayList<Object>();
        source.add(nestedMap);
        source.add(nestedSet);
        DatasetRow row = TestFixtures.row("nested", source);

        nestedBytes[0] = 9;
        nestedMap.put("extra", "changed");
        nestedSet.clear();
        source.clear();

        List<?> stored = (List<?>) row.get("nested");
        assertThat(stored).hasSize(2);
        Map<?, ?> storedMap = (Map<?, ?>) stored.get(0);
        Set<?> storedSet = (Set<?>) stored.get(1);
        assertThat((byte[]) storedMap.get("bytes")).containsExactly(6, 7);
        assertThat(storedMap.containsKey("extra")).isFalse();
        assertThat(storedSet).isEqualTo(Collections.singleton(new Date(4_000L)));
        assertThatThrownBy(() -> ((List<Object>) stored).add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<Object, Object>) storedMap).put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] returnedNestedBytes = (byte[]) storedMap.get("bytes");
        returnedNestedBytes[0] = 8;
        List<?> reread = (List<?>) row.get("nested");
        assertThat((byte[]) ((Map<?, ?>) reread.get(0)).get("bytes"))
                .containsExactly(6, 7);
        assertThatThrownBy(() -> DatasetRow.of("unsupported", new StringBuilder("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(StringBuilder.class.getName());
    }

    @Test
    void singleAllowsZeroOrOneRowAndRejectsMoreThanOne() {
        DatasetResult empty = DatasetResult.single(
                "emptySummary", Collections.<DatasetRow>emptyList());
        DatasetRow row = TestFixtures.row("total", 3);
        DatasetResult present = DatasetResult.single(
                "summary", Collections.singletonList(row));

        assertThat(empty.single()).isNull();
        assertThat(present.single()).isSameAs(row);
        assertThatThrownBy(() -> DatasetResult.single(
                "summary", Arrays.asList(DatasetRow.empty(), DatasetRow.empty())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void scalarAllowsZeroOrOneSingleFieldRowAndReturnsNullForNoRows() {
        DatasetResult empty = DatasetResult.scalar(
                "emptyCount", Collections.<DatasetRow>emptyList());
        DatasetResult present = DatasetResult.scalar(
                "count", Collections.singletonList(TestFixtures.row("value", 7)));

        assertThat(empty.scalar()).isNull();
        assertThat(present.scalar()).isEqualTo(7);
        assertThatThrownBy(() -> DatasetResult.scalar(
                "count", Arrays.asList(
                        TestFixtures.row("value", 1),
                        TestFixtures.row("value", 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
        assertThatThrownBy(() -> DatasetResult.scalar(
                "count", Collections.singletonList(
                        TestFixtures.row("value", 1, "other", 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one field");
    }

    @Test
    void accessorsRejectTheWrongResultShape() {
        DatasetResult list = DatasetResult.list(
                "people", Collections.<DatasetRow>emptyList());

        assertThatThrownBy(list::single)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIST");
        assertThatThrownBy(list::scalar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIST");
    }

    @Test
    void contextRejectsDuplicatesAndBuildsImmutableSnapshots() {
        DatasetResult first = DatasetResult.list(
                "first", Collections.<DatasetRow>emptyList());
        DatasetResult second = DatasetResult.list(
                "second", Collections.<DatasetRow>emptyList());
        DatasetContext.Builder builder = DatasetContext.builder().put(first);

        DatasetContext view = builder.buildView();
        builder.put(second);
        DatasetContext complete = builder.build();

        assertThat(view.ids()).containsExactly("first");
        assertThat(complete.ids()).containsExactly("first", "second");
        assertThat(complete.get("first")).isSameAs(first);
        assertThatThrownBy(() -> builder.put(first))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first");
        assertThatThrownBy(() -> view.get("second"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("second");
        assertThatThrownBy(() -> complete.asMap().put("third", first))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
