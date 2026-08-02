package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSdtBlock;

class WordTemplateChartBinderTest {

    @TempDir
    Path tempDir;

    @Test
    void replacesUniqueStandaloneSplitMarkerInSameParagraphAfterReopen()
            throws Exception {
        RenderedChart chart = chart();
        WordComponentDefinition component = new WordComponentDefinition();
        component.setChartId("approval");
        component.setAlignment("LEFT");
        component.setAltText("审批趋势图");
        Path output = tempDir.resolve("bound.docx");

        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph marker = document.createParagraph();
            marker.createRun().setText("{{chart:");
            marker.createRun().setText("approval}}");
            new WordTemplateChartBinder().bind(
                    document, "approval", chart, component);
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument reopened = new XWPFDocument(stream)) {
            assertThat(reopened.getParagraphs()).hasSize(1);
            XWPFParagraph paragraph = reopened.getParagraphs().get(0);
            assertThat(paragraph.getText()).doesNotContain("{{chart:");
            assertThat(paragraph.getAlignment())
                    .isEqualTo(ParagraphAlignment.LEFT);
            assertThat(paragraph.getRuns())
                    .anySatisfy(run -> assertThat(
                            run.getEmbeddedPictures()).hasSize(1));
        }
    }

    @Test
    void baseMarkerBindsAllGroupedChartsAsStableConsecutiveParagraphs()
            throws Exception {
        RenderedChart first = chart("first.png", 10);
        RenderedChart second = chart("second.png", 20);
        WordComponentDefinition component = new WordComponentDefinition();
        component.setChartId("approval");
        Path output = tempDir.resolve("grouped-bound.docx");

        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun()
                    .setText("{{chart:approval}}");
            document.createParagraph().createRun().setText("after");
            new WordTemplateChartBinder().bindAll(
                    document, "approval", Arrays.asList(first, second),
                    component);
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument reopened = new XWPFDocument(stream)) {
            assertThat(reopened.getParagraphs()).hasSize(3);
            assertThat(reopened.getParagraphs().get(0).getRuns())
                    .anySatisfy(run -> assertThat(
                            run.getEmbeddedPictures()).hasSize(1));
            assertThat(reopened.getParagraphs().get(1).getRuns())
                    .anySatisfy(run -> assertThat(
                            run.getEmbeddedPictures()).hasSize(1));
            assertThat(reopened.getParagraphs().get(2).getText())
                    .isEqualTo("after");
        }
    }

    @Test
    void rejectsEmbeddedOrPackageDuplicateMarkersIncludingSdt()
            throws Exception {
        RenderedChart chart = chart();
        WordComponentDefinition component = new WordComponentDefinition();
        component.setChartId("approval");

        try (XWPFDocument embedded = new XWPFDocument()) {
            embedded.createParagraph().createRun()
                    .setText("prefix {{chart:approval}}");
            assertThatThrownBy(() -> new WordTemplateChartBinder().bind(
                    embedded, "approval", chart, component))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("standalone");
        }

        try (XWPFDocument duplicate = new XWPFDocument()) {
            duplicate.createParagraph().createRun()
                    .setText("{{chart:approval}}");
            CTSdtBlock block =
                    duplicate.getDocument().getBody().addNewSdt();
            CTP content = block.addNewSdtContent().addNewP();
            new XWPFParagraph(content, duplicate)
                    .createRun().setText("{{chart:approval}}");
            assertThatThrownBy(() -> new WordTemplateChartBinder().bind(
                    duplicate, "approval", chart, component))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("exactly once");
        }
    }

    @Test
    void rejectsBodyAndFootnoteDuplicateAfterReopen()
            throws Exception {
        Path template = tempDir.resolve("duplicate-footnote.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun()
                    .setText("{{chart:approval}}");
            document.createFootnote().createParagraph().createRun()
                    .setText("{{chart:approval}}");
            try (OutputStream stream = Files.newOutputStream(template)) {
                document.write(stream);
            }
        }

        try (InputStream stream = Files.newInputStream(template);
             XWPFDocument reopened = new XWPFDocument(stream)) {
            WordComponentDefinition component =
                    new WordComponentDefinition();
            component.setChartId("approval");
            assertThatThrownBy(() -> new WordTemplateChartBinder().bind(
                    reopened, "approval", chart(), component))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("exactly once");
        }
    }

    private RenderedChart chart() throws Exception {
        return chart("chart.png", 0);
    }

    private RenderedChart chart(String fileName, int shade) throws Exception {
        Path image = tempDir.resolve(fileName);
        BufferedImage buffered = new BufferedImage(
                80, 40, BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0,
                new java.awt.Color(shade, shade, shade).getRGB());
        ImageIO.write(buffered, "png", image.toFile());
        return new RenderedChart(
                image, "image/png", 80, 40, 96);
    }
}
