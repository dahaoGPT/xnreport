package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

class WordImageWriterSectionTest {

    @TempDir
    Path tempDir;

    @Test
    void usesContainingSectionAndPreservesEveryRepeatedImageDrawing()
            throws Exception {
        Path png = createPng();
        RenderedChart chart =
                new RenderedChart(png, "image/png", 1600, 800, 200);
        Path output = tempDir.resolve("multi-section.docx");

        try (XWPFDocument document = new XWPFDocument()) {
            finalSection(document, 12240, 15840, 1440, 1440);
            WordImageWriter writer = new WordImageWriter();

            sectionBreak(document, 10080, 15840, 1440, 1440,
                    STPageOrientation.PORTRAIT);
            write(writer, document, chart, "LEFT");

            sectionBreak(document, 15840, 12240, 720, 720,
                    STPageOrientation.LANDSCAPE);
            write(writer, document, chart, "CENTER");

            sectionBreak(document, 12240, 15840, 2160, 2160,
                    STPageOrientation.PORTRAIT);
            write(writer, document, chart, "RIGHT");

            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument reopened = new XWPFDocument(stream)) {
            List<XWPFParagraph> drawings = drawingParagraphs(reopened);
            assertThat(drawings).hasSize(3);
            assertThat(drawings).extracting(XWPFParagraph::getAlignment)
                    .containsExactly(
                            ParagraphAlignment.LEFT,
                            ParagraphAlignment.CENTER,
                            ParagraphAlignment.RIGHT);
            assertThat(drawings).extracting(WordImageWriterSectionTest::width)
                    .containsExactly(
                            inches(5.0d),
                            inches(10.0d),
                            inches(5.5d));
            assertThat(drawings).extracting(WordImageWriterSectionTest::ratio)
                    .allSatisfy(value -> assertThat(value)
                            .isCloseTo(2.0d,
                                    org.assertj.core.data.Offset.offset(0.001d)));

            for (XWPFParagraph paragraph : drawings) {
                XWPFRun run = paragraph.getRuns().get(0);
                assertThat(run.getEmbeddedPictures()).hasSize(1);
                String relationId = run.getEmbeddedPictures().get(0)
                        .getCTPicture().getBlipFill().getBlip().getEmbed();
                POIXMLDocumentPart relation =
                        reopened.getRelationById(relationId);
                assertThat(relation).isNotNull();
                try (InputStream imageData =
                             relation.getPackagePart().getInputStream()) {
                    assertThat(imageData.read()).isNotEqualTo(-1);
                }
            }
        }
    }

    private Path createPng() throws Exception {
        Path image = tempDir.resolve("same-chart.png");
        BufferedImage source = new BufferedImage(
                1600, 800, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        ImageIO.write(source, "png", image.toFile());
        return image;
    }

    private static void write(
            WordImageWriter writer,
            XWPFDocument document,
            RenderedChart chart,
            String alignment) {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setWidthInches(Double.valueOf(20.0d));
        component.setAlignment(alignment);
        writer.write(document, document.createParagraph(), chart, component);
    }

    private static void sectionBreak(
            XWPFDocument document,
            long width,
            long height,
            long left,
            long right,
            STPageOrientation.Enum orientation) {
        XWPFParagraph boundary = document.createParagraph();
        boundary.createRun().setText("section boundary");
        CTSectPr section = boundary.getCTP().addNewPPr().addNewSectPr();
        settings(section, width, height, left, right);
        section.getPgSz().setOrient(orientation);
    }

    private static void finalSection(
            XWPFDocument document,
            long width,
            long height,
            long left,
            long right) {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        settings(section, width, height, left, right);
    }

    private static void settings(
            CTSectPr section,
            long width,
            long height,
            long left,
            long right) {
        CTPageSz size = section.addNewPgSz();
        size.setW(BigInteger.valueOf(width));
        size.setH(BigInteger.valueOf(height));
        CTPageMar margins = section.addNewPgMar();
        margins.setLeft(BigInteger.valueOf(left));
        margins.setRight(BigInteger.valueOf(right));
        margins.setTop(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
    }

    private static List<XWPFParagraph> drawingParagraphs(
            XWPFDocument document) {
        List<XWPFParagraph> result = new ArrayList<XWPFParagraph>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (!paragraph.getRuns().isEmpty()
                    && paragraph.getRuns().get(0).getCTR().sizeOfDrawingArray()
                    > 0) {
                result.add(paragraph);
            }
        }
        return result;
    }

    private static Long width(XWPFParagraph paragraph) {
        return Long.valueOf(paragraph.getRuns().get(0).getCTR()
                .getDrawingArray(0).getInlineArray(0).getExtent().getCx());
    }

    private static double ratio(XWPFParagraph paragraph) {
        long cx = paragraph.getRuns().get(0).getCTR()
                .getDrawingArray(0).getInlineArray(0).getExtent().getCx();
        long cy = paragraph.getRuns().get(0).getCTR()
                .getDrawingArray(0).getInlineArray(0).getExtent().getCy();
        return (double) cx / (double) cy;
    }

    private static Long inches(double value) {
        return Long.valueOf(Math.round(value * Units.EMU_PER_INCH));
    }
}
