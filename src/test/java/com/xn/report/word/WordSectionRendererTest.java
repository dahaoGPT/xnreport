package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordDefinition;
import com.xn.report.config.definition.WordNumberingDefinition;
import com.xn.report.config.definition.WordNumberingLevelDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

class WordSectionRendererTest {

    @Test
    void rendersRecursiveHeadingsAndComponentsInDepthFirstConfigurationOrder()
            throws Exception {
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            document.createParagraph().createRun().setText("模板尾部");
            WordSectionDefinition parent = section(
                    "delivery", "交付速率", 1, "KEEP");
            parent.setComponents(Arrays.asList(
                    text("SCENARIO", "场景说明"),
                    text("KEY_FACTORS", "构成要素"),
                    text("FIXED_TEXT", "固定说明"),
                    text("UNIT", "单位：小时")));
            WordSectionDefinition child = section(
                    "quality", "日均案例数", 2, "KEEP");
            child.setComponents(Collections.singletonList(
                    text("FIXED_TEXT", "子章节正文")));
            parent.setChildren(Collections.singletonList(child));

            WordSectionRenderer renderer = new WordSectionRenderer();
            renderer.render(document, Collections.singletonList(parent),
                    WordRenderContext.builder()
                            .datasets(DatasetContext.builder().build())
                            .build());

            XWPFDocument reopened = reopen(document);
            try {
                assertThat(reopened.getParagraphs())
                        .anyMatch(paragraph -> "Heading1".equals(
                                paragraph.getStyle())
                                && paragraph.getText().contains("交付速率"))
                        .anyMatch(paragraph -> "Heading2".equals(
                                paragraph.getStyle())
                                && paragraph.getText().contains("日均案例数"));
                try (XWPFWordExtractor extractor =
                             new XWPFWordExtractor(reopened)) {
                    String text = extractor.getText();
                    assertThat(text).doesNotContain("{{sections}}");
                    assertThat(text.indexOf("交付速率"))
                            .isLessThan(text.indexOf("场景说明"));
                    assertThat(text.indexOf("场景说明"))
                            .isLessThan(text.indexOf("构成要素"));
                    assertThat(text.indexOf("单位：小时"))
                            .isLessThan(text.indexOf("日均案例数"));
                    assertThat(text.indexOf("子章节正文"))
                            .isLessThan(text.indexOf("模板尾部"));
                }
            } finally {
                reopened.close();
            }
        }
    }

    @Test
    void usesConfiguredNumberingWithoutDuplicatingPrefixInHeadingText()
            throws Exception {
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            WordDefinition definition = new WordDefinition();
            WordNumberingDefinition numbering = new WordNumberingDefinition();
            WordNumberingLevelDefinition first =
                    numbering.getLevels().get(0);
            first.setNumFmt("decimal");
            first.setLvlText("第%1章");
            definition.setNumbering(numbering);
            definition.setSections(Collections.singletonList(
                    section("delivery", "交付速率", 1, "KEEP")));

            new WordSectionRenderer().render(document, definition,
                    WordRenderContext.builder()
                            .datasets(DatasetContext.builder().build())
                            .build());

            XWPFParagraph heading = document.getParagraphs().stream()
                    .filter(paragraph -> "Heading1".equals(
                            paragraph.getStyle()))
                    .findFirst().get();
            assertThat(heading.getText()).isEqualTo("交付速率");
            assertThat(heading.getText()).doesNotStartWith("第1章");
            assertThat(heading.getNumIlvl()).isEqualTo(BigInteger.ZERO);
        }
    }

    @Test
    void appliesKeepShowEmptyAndSkipBeforeCreatingHeadings() throws Exception {
        DatasetContext datasets = DatasetContext.builder()
                .put(DatasetResult.list("empty", Collections.emptyList()))
                .build();
        WordSectionDefinition keep = section("keep", "保留章节", 1, "KEEP");
        WordSectionDefinition show =
                section("show", "空态章节", 1, "SHOW_EMPTY");
        show.setEmptyMessage("暂无审批数据");
        show.setComponents(Collections.singletonList(table("empty")));
        WordSectionDefinition skip =
                section("skip", "跳过章节", 1, "SKIP");
        skip.setComponents(Collections.singletonList(table("empty")));

        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            new WordSectionRenderer().render(
                    document, Arrays.asList(keep, show, skip),
                    WordRenderContext.builder().datasets(datasets).build());
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                assertThat(extractor.getText())
                        .contains("保留章节")
                        .contains("空态章节")
                        .contains("暂无审批数据")
                        .doesNotContain("跳过章节");
            }
        }
    }

    @Test
    void rejectsEmbeddedSectionsAnchorBeforeRendering() throws Exception {
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            XWPFParagraph anchor = document.getParagraphs().stream()
                    .filter(paragraph ->
                            "{{sections}}".equals(paragraph.getText()))
                    .findFirst().get();
            anchor.createRun().setText(" suffix");

            assertThatThrownBy(() -> new WordSectionRenderer().render(
                    document, Collections.<WordSectionDefinition>emptyList(),
                    WordRenderContext.builder()
                            .datasets(DatasetContext.builder().build())
                            .build()))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("standalone top-level body");
            assertThat(anchor.getText()).isEqualTo("{{sections}} suffix");
        }
    }

    private static WordSectionDefinition section(
            String id, String title, int level, String strategy) {
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId(id);
        section.setTitle(title);
        section.setLevel(level);
        section.setEmptyStrategy(strategy);
        return section;
    }

    private static WordComponentDefinition text(String type, String value) {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setType(type);
        component.setText(value);
        return component;
    }

    private static WordComponentDefinition table(String dataset) {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setType("TABLE");
        component.setTableId(dataset + "Table");
        component.setDataset(dataset);
        return component;
    }

    private static XWPFDocument reopen(XWPFDocument document) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        return new XWPFDocument(new ByteArrayInputStream(output.toByteArray()));
    }
}
