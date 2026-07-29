package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordOutputValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void reopensAndValidatesTocStylesOrderAndResolvedPlaceholders()
            throws Exception {
        Path output = tempDir.resolve("valid.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            document.getParagraphs().stream()
                    .filter(p -> p.getText().contains("{{sections}}"))
                    .findFirst().get().getRuns().get(0).setText("正文", 0);
            document.createParagraph().setStyle("Heading1");
            document.getParagraphs().get(
                    document.getParagraphs().size() - 1)
                    .createRun().setText("交付速率");
            document.getSettings().setUpdateFields();
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        assertThatCode(() -> new WordOutputValidator().validate(
                output, 3, java.util.Collections.singletonList("交付速率"),
                0, 0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnresolvedPlaceholder() throws Exception {
        Path output = tempDir.resolve("invalid.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate();
             OutputStream stream = Files.newOutputStream(output)) {
            document.getSettings().setUpdateFields();
            document.write(stream);
        }

        assertThatThrownBy(() -> new WordOutputValidator().validate(
                output, 3, java.util.Collections.emptyList(), 0, 0))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("unresolved");
    }

    @Test
    void rejectsUnresolvedPlaceholderInsideContentControl()
            throws Exception {
        Path output = tempDir.resolve("invalid-sdt.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            prepareEmptyOutput(document);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSdtBlock
                    block = document.getDocument().getBody().addNewSdt();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP
                    content = block.addNewSdtContent().addNewP();
            new org.apache.poi.xwpf.usermodel.XWPFParagraph(
                    content, document).createRun()
                    .setText("{{value:hidden}}");
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        assertThatThrownBy(() -> new WordOutputValidator().validate(
                output, WordOutputExpectation.builder().build()))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("unresolved");
    }

    @Test
    void validatesCompleteImmutableOutputExpectation() throws Exception {
        Path image = tempDir.resolve("chart.png");
        ImageIO.write(new BufferedImage(
                20, 10, BufferedImage.TYPE_INT_RGB),
                "png", image.toFile());
        DatasetResult details = DatasetResult.single(
                "details", Collections.singletonList(
                        TestFixtures.row("name", "张三", "hours", 12.5)));
        Path output = tempDir.resolve("complete.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            addCoverTokens(document);
            com.xn.report.config.definition.WordCoverDefinition cover =
                    new com.xn.report.config.definition.WordCoverDefinition();
            cover.setTitle("研发效能报告");
            cover.setOrganization("软件开发二中心");
            cover.setReportPeriod("2026年6月");
            cover.setPreparedBy("效能小组");
            cover.setPreparedDate("2026年7月23日");
            new WordCoverBinder(new WordRunTextReplacer())
                    .bind(document, cover);
            new WordTocManager().configure(document, 3, true);

            WordSectionDefinition first = section("first", "交付速率", 1);
            WordSectionDefinition second = section("second", "交付质量", 2);
            WordSectionDefinition third = section("third", "审批分析", 3);
            WordSectionDefinition fourth = section("fourth", "人员明细", 4);
            WordComponentDefinition table = new WordComponentDefinition();
            table.setType("TABLE");
            table.setDataset("details");
            WordComponentDefinition attachment =
                    new WordComponentDefinition();
            attachment.setType("ATTACHMENT");
            attachment.setTitle("附件信息");
            attachment.setDescription("附件说明");
            attachment.setItems(Arrays.asList("人员明细.xlsx", "规则说明.pdf"));
            WordComponentDefinition chart = new WordComponentDefinition();
            chart.setType("CHART");
            chart.setChartId("trend");
            fourth.setComponents(Arrays.asList(table, attachment, chart));
            third.setChildren(Collections.singletonList(fourth));
            second.setChildren(Collections.singletonList(third));
            first.setChildren(Collections.singletonList(second));
            new WordSectionRenderer().render(
                    document, Collections.singletonList(first),
                    WordRenderContext.builder()
                            .datasets(DatasetContext.builder()
                                    .put(details).build())
                            .chart("trend", new RenderedChart(
                                    image, "image/png", 20, 10, 96))
                            .build());
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        WordOutputExpectation expectation =
                WordOutputExpectation.builder()
                        .cover("研发效能报告", "软件开发二中心",
                                "2026年6月", "效能小组", "2026年7月23日")
                        .tocMaxLevel(3)
                        .requireUpdateFields(true)
                        .heading(1, "交付速率")
                        .heading(2, "交付质量")
                        .heading(3, "审批分析")
                        .heading(4, "人员明细")
                        .table(0, 2,
                                Arrays.asList("name", "hours", "张三", "12.5"))
                        .attachment(
                                "附件信息", "附件说明",
                                Arrays.asList(
                                        "人员明细.xlsx", "规则说明.pdf"))
                        .pictureInstances(1)
                        .build();

        assertThatCode(() -> new WordOutputValidator().validate(
                output, expectation)).doesNotThrowAnyException();
    }

    @Test
    void rejectsCoverValuesScatteredOutsideBoundCoverStructure()
            throws Exception {
        Path output = tempDir.resolve("fake-cover.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            prepareEmptyOutput(document);
            document.createParagraph().createRun().setText("研发效能报告");
            document.createParagraph().createRun().setText("研发中心");
            document.createParagraph().createRun().setText("2026年6月");
            document.createParagraph().createRun().setText("效能小组");
            document.createParagraph().createRun().setText("2026年7月23日");
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }
        WordOutputExpectation expectation =
                WordOutputExpectation.builder()
                        .cover("研发效能报告", "研发中心", "2026年6月",
                                "效能小组", "2026年7月23日")
                        .build();

        assertThatThrownBy(() ->
                new WordOutputValidator().validate(output, expectation))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("cover")
                .hasMessageContaining("structure");
    }

    @Test
    void rejectsAttachmentTextOutsideAttachmentComponentParagraphs()
            throws Exception {
        Path output = tempDir.resolve("fake-attachment.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            prepareEmptyOutput(document);
            for (String text : Arrays.asList(
                    "附件信息", "附件说明",
                    "人员明细.xlsx", "规则说明.pdf")) {
                document.createParagraph().createRun().setText(text);
            }
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }
        WordOutputExpectation expectation =
                WordOutputExpectation.builder()
                        .attachment("附件信息", "附件说明",
                                Arrays.asList(
                                        "人员明细.xlsx", "规则说明.pdf"))
                        .build();

        assertThatThrownBy(() ->
                new WordOutputValidator().validate(output, expectation))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("attachment")
                .hasMessageContaining("structure");
    }

    private static WordSectionDefinition section(
            String id, String title, int level) {
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId(id);
        section.setTitle(title);
        section.setLevel(level);
        section.setEmptyStrategy("KEEP");
        return section;
    }

    private static void addCoverTokens(XWPFDocument document) {
        String[] tokens = {
            WordCoverBinder.REPORT_TITLE,
            WordCoverBinder.ORGANIZATION,
            WordCoverBinder.REPORT_PERIOD,
            WordCoverBinder.PREPARED_BY,
            WordCoverBinder.PREPARED_DATE
        };
        org.apache.xmlbeans.XmlObject anchor =
                ((org.apache.poi.xwpf.usermodel.XWPFParagraph)
                        document.getBodyElements().get(0)).getCTP();
        for (String token : tokens) {
            org.apache.xmlbeans.XmlCursor cursor = anchor.newCursor();
            try {
                if (WordCoverBinder.REPORT_PERIOD.equals(token)) {
                    org.apache.poi.xwpf.usermodel.XWPFTable table =
                            document.insertNewTbl(cursor);
                    table.getRow(0).getCell(0).setText("时间");
                    table.getRow(0).addNewTableCell().setText(token);
                } else {
                    document.insertNewParagraph(cursor)
                            .createRun().setText(token);
                }
            } finally {
                cursor.dispose();
            }
        }
    }

    private static void prepareEmptyOutput(XWPFDocument document) {
        document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getText()
                        .contains("{{sections}}"))
                .findFirst().get().getRuns().get(0).setText("正文", 0);
        new WordTocManager().configure(document, 3, true);
    }
}
