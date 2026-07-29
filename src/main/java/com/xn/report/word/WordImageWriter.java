package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordImageAlignment;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

public final class WordImageWriter {

    private static final long DEFAULT_PAGE_WIDTH_DXA = 12240L;
    private static final long DEFAULT_MARGIN_DXA = 1440L;

    public void write(
            XWPFDocument document,
            XWPFParagraph paragraph,
            RenderedChart chart,
            WordComponentDefinition component) {
        if (document == null || paragraph == null
                || chart == null || component == null) {
            throw new IllegalArgumentException(
                    "Word document, paragraph, chart and component are required");
        }
        if (!"image/png".equalsIgnoreCase(chart.getMediaType())
                || !Files.isRegularFile(chart.getPath())) {
            throw new WordTemplateException(
                    "Word chart image must be an existing PNG file");
        }
        PrintableArea printable = printableAreaEmu(document, paragraph);
        double requested = component.getWidthInches() == null
                ? (double) chart.getWidthPixels() / Math.max(1, chart.getDpi())
                : component.getWidthInches().doubleValue();
        long width = Math.max(1L, Math.round(
                requested * Units.EMU_PER_INCH));
        long height = Math.max(1L, Math.round(
                width * (double) chart.getHeightPixels()
                        / (double) chart.getWidthPixels()));
        double scale = Math.min(1.0d, Math.min(
                printable.width / (double) width,
                printable.height / (double) height));
        width = Math.max(1L, Math.round(width * scale));
        height = Math.max(1L, Math.round(height * scale));
        paragraph.setAlignment(alignment(component.getAlignment()));
        clearRuns(paragraph);
        XWPFRun run = paragraph.createRun();
        try (InputStream input = Files.newInputStream(chart.getPath())) {
            run.addPicture(input, Document.PICTURE_TYPE_PNG,
                    chart.getPath().getFileName().toString(),
                    Math.toIntExact(width), Math.toIntExact(height));
        } catch (Exception ex) {
            throw new WordTemplateException(
                    "Unable to insert Word chart image " + chart.getPath(), ex);
        }
        org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline
                inline = run.getCTR().getDrawingArray(0).getInlineArray(0);
        if (component.getAltText() != null) {
            inline.getDocPr().setDescr(component.getAltText());
            inline.getDocPr().setTitle(component.getAltText());
        }
        if (component.getCaption() != null
                && !component.getCaption().trim().isEmpty()) {
            XWPFParagraph caption = insertAfter(document, paragraph);
            caption.setAlignment(ParagraphAlignment.CENTER);
            caption.createRun().setText(component.getCaption());
        }
    }

    private static XWPFParagraph insertAfter(
            XWPFDocument document, XWPFParagraph paragraph) {
        java.util.List<IBodyElement> body = document.getBodyElements();
        int index = -1;
        for (int candidate = 0; candidate < body.size(); candidate++) {
            if (body.get(candidate) == paragraph) {
                index = candidate;
                break;
            }
        }
        if (index < 0 || index + 1 >= body.size()) {
            return document.createParagraph();
        }
        IBodyElement next = body.get(index + 1);
        XmlObject xml = next instanceof XWPFParagraph
                ? ((XWPFParagraph) next).getCTP()
                : ((XWPFTable) next).getCTTbl();
        XmlCursor cursor = xml.newCursor();
        try {
            return document.insertNewParagraph(cursor);
        } finally {
            cursor.dispose();
        }
    }

    private static PrintableArea printableAreaEmu(
            XWPFDocument document, XWPFParagraph insertion) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr() : null;
        for (IBodyElement element : document.getBodyElements()) {
            if (element == insertion) {
                break;
            }
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                if (paragraph.getCTP().isSetPPr()
                        && paragraph.getCTP().getPPr().isSetSectPr()) {
                    section = paragraph.getCTP().getPPr().getSectPr();
                }
            }
        }
        long pageWidth = DEFAULT_PAGE_WIDTH_DXA;
        long pageHeight = 15840L;
        long left = DEFAULT_MARGIN_DXA;
        long right = DEFAULT_MARGIN_DXA;
        long top = DEFAULT_MARGIN_DXA;
        long bottom = DEFAULT_MARGIN_DXA;
        if (section != null && section.isSetPgSz()) {
            CTPageSz pageSize = section.getPgSz();
            pageWidth = number(pageSize.getW(), pageWidth);
            pageHeight = number(pageSize.getH(), pageHeight);
        }
        if (section != null && section.isSetPgMar()) {
            CTPageMar margins = section.getPgMar();
            left = number(margins.getLeft(), left);
            right = number(margins.getRight(), right);
            top = number(margins.getTop(), top);
            bottom = number(margins.getBottom(), bottom);
        }
        return new PrintableArea(
                dxaToEmu(pageWidth - left - right),
                dxaToEmu(pageHeight - top - bottom));
    }

    private static long dxaToEmu(long value) {
        return Math.max(1L, Math.round(
                value / 1440.0d * Units.EMU_PER_INCH));
    }

    private static ParagraphAlignment alignment(String configured) {
        switch (WordImageAlignment.fromConfig(configured)) {
            case LEFT:
                return ParagraphAlignment.LEFT;
            case RIGHT:
                return ParagraphAlignment.RIGHT;
            case CENTER:
            default:
                return ParagraphAlignment.CENTER;
        }
    }

    private static long number(Object value, long fallback) {
        if (value instanceof BigInteger) {
            return ((BigInteger) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return fallback;
    }

    private static void clearRuns(XWPFParagraph paragraph) {
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }
    }

    private static final class PrintableArea {
        private final long width;
        private final long height;

        private PrintableArea(long width, long height) {
            this.width = width;
            this.height = height;
        }
    }
}
