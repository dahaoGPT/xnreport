package com.xn.report.support;

import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportMetadata;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static ReportDefinition report(DatasetDefinition... datasets) {
        ReportMetadata metadata = new ReportMetadata();
        metadata.setCode("test-report");
        metadata.setName("Test Report");

        ReportDefinition definition = new ReportDefinition();
        definition.setSchemaVersion("1.0");
        definition.setReport(metadata);
        definition.setDatasets(Arrays.asList(datasets));
        return definition;
    }

    public static DatasetDefinition dataset(String id, String... dependsOn) {
        return dataset(id, id + ".sql", null, dependsOn);
    }

    public static DatasetDefinition dataset(
            String id, String sqlFile, String sql, String... dependsOn) {
        DatasetDefinition dataset = new DatasetDefinition();
        dataset.setId(id);
        dataset.setSheetName("Sheet-" + id);
        dataset.setSqlFile(sqlFile);
        dataset.setSql(sql);
        dataset.setResultType(DatasetType.LIST);
        dataset.setDependsOn(Arrays.asList(dependsOn));
        return dataset;
    }

    public static DatasetRow row(Object... pairs) {
        return DatasetRow.of(pairs);
    }

    public static DatasetRow person(String name, String avgHours) {
        return DatasetRow.of(
                "personName", name,
                "avgHours", new BigDecimal(avgHours));
    }

    public static DatasetResult people(DatasetRow... rows) {
        return DatasetResult.list("people", Arrays.asList(rows));
    }

    public static Map<String, Object> parameters(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be pairs");
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return values;
    }
}
