package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordImageWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void insertsInlinePngPreservingAspectRatioAndCappingPrintableWidth()
            throws Exception {
        Path image = tempDir.resolve("wide.png");
        BufferedImage source = new BufferedImage(
                1600, 800, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        ImageIO.write(source, "png", image.toFile());
        RenderedChart chart =
                new RenderedChart(image, "image/png", 1600, 800, 200);
        WordComponentDefinition component = new WordComponentDefinition();
        component.setWidthInches(Double.valueOf(20));
        component.setCaption("图1 审批时长");
        component.setAltText("审批时长趋势图");

        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            new WordImageWriter().write(document, paragraph, chart, component);

            assertThat(document.getAllPictures()).hasSize(1);
            assertThat(paragraph.getRuns().get(0).getEmbeddedPictures()).hasSize(1);
            org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline
                    inline = paragraph.getRuns().get(0).getCTR()
                    .getDrawingArray(0).getInlineArray(0);
            assertThat(inline.getExtent().getCx())
                    .isLessThanOrEqualTo(5943600L);
            assertThat((double) inline.getExtent().getCx()
                    / (double) inline.getExtent().getCy())
                    .isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.01));
            assertThat(inline.getDocPr().getDescr())
                    .isEqualTo("审批时长趋势图");
            assertThat(document.getParagraphs().get(1).getText())
                    .isEqualTo("图1 审批时长");
        }
    }
}
