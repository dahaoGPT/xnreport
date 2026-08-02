package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.junit.jupiter.api.Test;

class WordTableWriterTest {

    private final WordTableWriter writer = new WordTableWriter();

    @Test
    void clonesPrototypeRowForEveryListRowAndPreservesHeader() throws Exception {
        try (XWPFDocument document = ReportTemplateFixtureBuilder.build()) {
            XWPFTable table = document.getTables().get(0);
            DatasetResult dataset = TestFixtures.people(
                    TestFixtures.row("personName", "张三",
                            "centerName", "开发一中心", "avgHours", 12.345),
                    TestFixtures.row("personName", "李四",
                            "centerName", "开发二中心", "avgHours", 8));

            int rows = writer.bindPrototype(table, dataset, "暂无数据");

            assertThat(rows).isEqualTo(2);
            assertThat(table.getNumberOfRows()).isEqualTo(3);
            assertThat(table.getRow(0).getCell(0).getText()).isEqualTo("姓名");
            assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("张三");
            assertThat(table.getRow(1).getCell(2).getText()).isEqualTo("12.35");
            assertThat(table.getRow(2).getCell(0).getText()).isEqualTo("李四");
        }
    }

    @Test
    void persistsEveryClonedPrototypeReplacementAfterSerialization()
            throws Exception {
        try (XWPFDocument document = ReportTemplateFixtureBuilder.build()) {
            XWPFTable table = document.getTables().get(0);
            DatasetResult dataset = TestFixtures.people(
                    TestFixtures.row("personName", "张三",
                            "centerName", "开发一中心", "avgHours", 12.3),
                    TestFixtures.row("personName", "李四",
                            "centerName", "开发二中心", "avgHours", 8),
                    TestFixtures.row("personName", "王五",
                            "centerName", "研发中心", "avgHours", 23.6));

            writer.bindPrototype(table, dataset, "暂无数据");

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.write(bytes);
            try (XWPFDocument reopened = new XWPFDocument(
                    new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(reopened.getTables().get(0).getText())
                        .contains("张三", "李四", "王五")
                        .doesNotContain("{{row:");
            }
        }
    }

    @Test
    void preservesPrototypeGeometryAndAlignmentAfterSerialization()
            throws Exception {
        try (XWPFDocument document = ReportTemplateFixtureBuilder.build()) {
            XWPFTable table = document.getTables().get(0);
            table.setTableAlignment(TableRowAlign.RIGHT);
            table.setWidth("7200");
            int[] widths = {1200, 2400, 3600};
            for (org.apache.poi.xwpf.usermodel.XWPFTableRow row
                    : table.getRows()) {
                for (int index = 0; index < widths.length; index++) {
                    row.getCell(index).setWidth(String.valueOf(widths[index]));
                }
            }

            writer.bindPrototype(table, TestFixtures.people(
                    TestFixtures.row("personName", "张三",
                            "centerName", "开发中心", "avgHours", 12.3),
                    TestFixtures.row("personName", "李四",
                            "centerName", "研发中心", "avgHours", 8.0)),
                    "暂无数据");

            try (XWPFDocument reopened = reopen(document)) {
                XWPFTable actual = reopened.getTables().get(0);
                assertThat(actual.getTableAlignment())
                        .isEqualTo(TableRowAlign.RIGHT);
                assertThat(actual.getWidth()).isEqualTo(7200);
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row
                        : actual.getRows()) {
                    assertThat(row.getTableCells())
                            .extracting(cell -> cell.getWidth())
                            .containsExactly(1200, 2400, 3600);
                }
            }
        }
    }

    @Test
    void replacesPrototypeWithConfiguredMessageForEmptyDataset() throws Exception {
        try (XWPFDocument document = ReportTemplateFixtureBuilder.build()) {
            XWPFTable table = document.getTables().get(0);
            DatasetResult empty = DatasetResult.list(
                    "people", Collections.emptyList());

            int rows = writer.bindPrototype(table, empty, "暂无明细");

            assertThat(rows).isZero();
            assertThat(table.getNumberOfRows()).isEqualTo(2);
            assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("暂无明细");
            assertThat(table.getRow(1).getCell(1).getText()).isEmpty();
        }
    }

    @Test
    void writesGeneratedTablesForSingleAndScalarDatasets() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFTable singleTable = document.createTable();
            writer.fillGenerated(
                    singleTable,
                    DatasetResult.single("summary", Collections.singletonList(
                            TestFixtures.row("name", "开发中心", "hours", 12.345))),
                    Collections.emptyList(),
                    "暂无数据");

            XWPFTable scalarTable = document.createTable();
            writer.fillGenerated(
                    scalarTable,
                    DatasetResult.scalar("count", Collections.singletonList(
                            TestFixtures.row("count", 7))),
                    Collections.emptyList(),
                    "暂无数据");

            assertThat(singleTable.getNumberOfRows()).isEqualTo(2);
            assertThat(singleTable.getRow(1).getTableCells())
                    .extracting(cell -> cell.getText())
                    .containsExactly("开发中心", "12.345");
            assertThat(scalarTable.getNumberOfRows()).isEqualTo(2);
            assertThat(scalarTable.getRow(0).getCell(0).getText())
                    .isEqualTo("count");
            assertThat(scalarTable.getRow(1).getCell(0).getText())
                    .isEqualTo("7");
            assertThat(singleTable.getRow(0).getCtRow().getTrPr()
                    .sizeOfTblHeaderArray()).isEqualTo(1);
        }
    }

    @Test
    void persistsGeneratedTableGridIndentAndEveryRowWidth()
            throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFTable table = document.createTable();
            writer.fillGenerated(
                    table,
                    DatasetResult.list("centers", Arrays.asList(
                            TestFixtures.row(
                                    "name", "开发一中心",
                                    "hours", 12.3,
                                    "baseline", 10.0,
                                    "status", "正常"),
                            TestFixtures.row(
                                    "name", "开发二中心",
                                    "hours", 18.5,
                                    "baseline", 10.0,
                                    "status", "关注"))),
                    Collections.emptyList(),
                    "暂无数据");

            try (XWPFDocument reopened = reopen(document)) {
                XWPFTable actual = reopened.getTables().get(0);
                assertThat(actual.getCTTbl().getTblGrid()
                        .getGridColList())
                        .extracting(column -> column.getW().toString())
                        .containsExactly("2200", "2200", "2200", "2200");
                assertThat(actual.getCTTbl().getTblPr().getTblInd()
                        .getW().toString()).isEqualTo("120");
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row
                        : actual.getRows()) {
                    assertThat(row.getTableCells())
                            .extracting(cell -> cell.getWidth())
                            .containsExactly(2200, 2200, 2200, 2200);
                }
            }
        }
    }

    private static XWPFDocument reopen(XWPFDocument document)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        return new XWPFDocument(
                new ByteArrayInputStream(output.toByteArray()));
    }
}
