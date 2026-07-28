package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.support.TestFixtures;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
