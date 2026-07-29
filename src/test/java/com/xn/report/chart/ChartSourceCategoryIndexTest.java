package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.xn.report.config.definition.ChartDefinition;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChartSourceCategoryIndexTest {

    @Test
    void indexesLargeGroupedTypedCategoriesInLinearTime() {
        ChartDefinition definition = new ChartDefinition();
        definition.setCategoryField("category");
        definition.setGroupByField("group");
        List<DatasetRow> rows = new ArrayList<DatasetRow>();
        for (int index = 0; index < 5000; index++) {
            rows.add(DatasetRow.of(
                    "group", "A", "category", index));
            rows.add(DatasetRow.of(
                    "group", "B", "category", index));
        }
        DatasetResult result = DatasetResult.list("large", rows);

        assertTimeout(Duration.ofSeconds(2), () -> {
            ChartSourceCategoryIndex index =
                    ChartSourceCategoryIndex.build(
                            definition, result, "B");
            for (int category = 0; category < 5000; category++) {
                assertThat(index.source(String.valueOf(category)))
                        .isEqualTo(category);
            }
        });
    }

    @Test
    void returnsTheFirstTypedRawValueAndFallsBackForMissingLabels() {
        ChartDefinition definition = new ChartDefinition();
        definition.setCategoryField("category");
        DatasetResult result = DatasetResult.list(
                "typed", java.util.Arrays.asList(
                        DatasetRow.of("category", 7),
                        DatasetRow.of("category", 7)));

        ChartSourceCategoryIndex index =
                ChartSourceCategoryIndex.build(
                        definition, result, null);

        assertThat(index.source("7")).isEqualTo(7);
        assertThat(index.source("missing")).isEqualTo("missing");
    }
}
