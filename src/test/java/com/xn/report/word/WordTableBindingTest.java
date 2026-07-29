package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.config.definition.WordTableBinding;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class WordTableBindingTest {

    @Test
    void bindsListIntoLocatedPrototypeAndRemovesMarkerAfterSerialization()
            throws Exception {
        DatasetResult data = TestFixtures.people(
                TestFixtures.row("personName", "张三",
                        "centerName", "开发一中心", "avgHours", 12.3),
                TestFixtures.row("personName", "李四",
                        "centerName", "开发二中心", "avgHours", 8));
        WordDefinition definition = definition(
                binding("peopleBinding", "people",
                        "{{table:people}}", null, "PROTOTYPE"),
                tableComponent("peopleBinding", null));

        try (XWPFDocument document = templateWithPrototype(
                "{{table:people}}")) {
            new WordSectionRenderer().render(
                    document, definition,
                    WordRenderContext.builder().datasets(
                            DatasetContext.builder().put(data).build()).build());

            try (XWPFDocument reopened = reopen(document)) {
                assertThat(reopened.getTables()).hasSize(1);
                assertThat(reopened.getTables().get(0).getNumberOfRows())
                        .isEqualTo(3);
                assertThat(reopened.getTables().get(0).getText())
                        .contains("张三", "李四")
                        .doesNotContain("{{table:", "{{row:");
            }
        }
    }

    @Test
    void bindsSingleAndScalarPrototypeDatasets() throws Exception {
        assertPrototypeValue(
                DatasetResult.single("value", Collections.singletonList(
                        TestFixtures.row("personName", "单行",
                                "centerName", "中心", "avgHours", 1))),
                "单行");
        assertPrototypeValue(
                DatasetResult.scalar("value", Collections.singletonList(
                        TestFixtures.row("personName", "标量"))),
                "标量");
    }

    @Test
    void appliesSkipAndShowEmptyPoliciesWithoutLeavingPrototypeTokens()
            throws Exception {
        DatasetResult empty = DatasetResult.list(
                "people", Collections.emptyList());
        WordTableBinding skip = binding(
                "skip", "people", "{{table:skip}}", null, "PROTOTYPE");
        skip.setEmptyStrategy("SKIP");
        WordTableBinding show = binding(
                "show", "people", "{{table:show}}", null, "PROTOTYPE");
        show.setEmptyStrategy("SHOW_EMPTY");
        show.setEmptyMessage("暂无审批明细");
        WordDefinition definition = new WordDefinition();
        definition.setTableBindings(Arrays.asList(skip, show));
        WordSectionDefinition section = section();
        section.setComponents(Arrays.asList(
                tableComponent("skip", null),
                tableComponent("show", null)));
        definition.setSections(Collections.singletonList(section));

        try (XWPFDocument document = templateWithTwoPrototypes()) {
            new WordSectionRenderer().render(
                    document, definition,
                    WordRenderContext.builder().datasets(
                            DatasetContext.builder().put(empty).build()).build());
            try (XWPFDocument reopened = reopen(document)) {
                assertThat(reopened.getTables()).hasSize(1);
                assertThat(reopened.getTables().get(0).getText())
                        .contains("暂无审批明细")
                        .doesNotContain("{{row:", "{{table:");
            }
        }
    }

    @Test
    void usesGeneratedTableOnlyForExplicitDatasetWithoutBinding()
            throws Exception {
        DatasetResult data = DatasetResult.single(
                "summary", Collections.singletonList(
                        TestFixtures.row("name", "研发中心", "hours", 12.5)));
        WordDefinition definition = definition(
                null, tableComponent(null, "summary"));

        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            new WordSectionRenderer().render(
                    document, definition,
                    WordRenderContext.builder().datasets(
                            DatasetContext.builder().put(data).build()).build());
            try (XWPFDocument reopened = reopen(document)) {
                assertThat(reopened.getTables()).hasSize(1);
                assertThat(reopened.getTables().get(0).getText())
                        .contains("研发中心", "12.5");
            }
        }
    }

    private static void assertPrototypeValue(
            DatasetResult dataset, String expected) throws Exception {
        WordDefinition definition = definition(
                binding("valueBinding", "value",
                        "{{table:value}}", null, "PROTOTYPE"),
                tableComponent("valueBinding", null));
        try (XWPFDocument document = templateWithPrototype(
                "{{table:value}}")) {
            new WordSectionRenderer().render(
                    document, definition,
                    WordRenderContext.builder().datasets(
                            DatasetContext.builder().put(dataset).build()).build());
            try (XWPFDocument reopened = reopen(document)) {
                assertThat(reopened.getTables().get(0).getText())
                        .contains(expected)
                        .doesNotContain("{{row:");
            }
        }
    }

    private static WordDefinition definition(
            WordTableBinding binding, WordComponentDefinition component) {
        WordDefinition definition = new WordDefinition();
        if (binding != null) {
            definition.setTableBindings(Collections.singletonList(binding));
        }
        WordSectionDefinition section = section();
        section.setComponents(Collections.singletonList(component));
        definition.setSections(Collections.singletonList(section));
        return definition;
    }

    private static WordSectionDefinition section() {
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId("details");
        section.setTitle("明细");
        section.setLevel(1);
        section.setEmptyStrategy("KEEP");
        return section;
    }

    private static WordComponentDefinition tableComponent(
            String bindingId, String dataset) {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setType("TABLE");
        component.setTableId(bindingId);
        component.setDataset(dataset);
        return component;
    }

    private static WordTableBinding binding(
            String id,
            String dataset,
            String marker,
            String tableId,
            String strategy) {
        WordTableBinding binding = new WordTableBinding();
        binding.setId(id);
        binding.setDataset(dataset);
        binding.setMarker(marker);
        binding.setTableId(tableId);
        binding.setStrategy(strategy);
        return binding;
    }

    private static XWPFDocument templateWithPrototype(String marker) {
        XWPFDocument document = WordTemplateLoaderTest.validTemplate();
        document.createParagraph().createRun().setText(marker);
        prototype(document);
        return document;
    }

    private static XWPFDocument templateWithTwoPrototypes() {
        XWPFDocument document = WordTemplateLoaderTest.validTemplate();
        document.createParagraph().createRun().setText("{{table:skip}}");
        prototype(document);
        document.createParagraph().createRun().setText("{{table:show}}");
        prototype(document);
        return document;
    }

    private static void prototype(XWPFDocument document) {
        XWPFTable table = document.createTable(2, 3);
        table.getRow(0).getCell(0).setText("姓名");
        table.getRow(0).getCell(1).setText("中心");
        table.getRow(0).getCell(2).setText("耗时");
        table.getRow(1).getCell(0).setText("{{row:personName}}");
        table.getRow(1).getCell(1).setText("{{row:centerName}}");
        table.getRow(1).getCell(2)
                .setText("{{row:avgHours|number:0.00}}");
    }

    private static XWPFDocument reopen(XWPFDocument document)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        return new XWPFDocument(
                new ByteArrayInputStream(output.toByteArray()));
    }
}
