package com.xn.report.word;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/**
 * Deterministic source for the binary Word fixture. Run only when the fixture
 * contract changes.
 */
public final class ReportTemplateFixtureBuilder {

    private ReportTemplateFixtureBuilder() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0
                ? Paths.get("src/test/resources/fixtures/templates/report-template.docx")
                : Paths.get(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (XWPFDocument document = build();
             OutputStream stream = Files.newOutputStream(output)) {
            document.write(stream);
        }
    }

    static XWPFDocument build() {
        XWPFDocument document = new XWPFDocument();
        createStyles(document);
        createPageSettings(document);

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontFamily("SimSun");
        titleRun.setFontSize(32);
        titleRun.setText("{{cover:");
        XWPFRun titleTail = title.createRun();
        titleTail.setBold(true);
        titleTail.setFontFamily("SimSun");
        titleTail.setFontSize(32);
        titleTail.setText("title}}");

        centered(document, "{{cover:organization}}", 18);
        centered(document, "时 间  {{cover:reportPeriod}}", 16);
        for (int index = 0; index < 12; index++) {
            document.createParagraph();
        }
        centered(document, "{{cover:preparedBy}}", 14);
        centered(document, "{{cover:preparedDate}}", 14);
        pageBreak(document);

        centered(document, "目 录", 22);
        WordTocManagerTest.addComplexToc(document);
        pageBreak(document);

        XWPFParagraph sections = document.createParagraph();
        sections.createRun().setText("{{sec");
        sections.createRun().setText("tions}}");

        XWPFParagraph value = document.createParagraph();
        value.createRun().setText("指标：{{value:");
        value.createRun().setText("teamSummary.avgHours}}");
        XWPFParagraph narrative = document.createParagraph();
        narrative.createRun().setText("{{text:");
        narrative.createRun().setText("approvalTimeout}}");

        XWPFTable table = document.createTable(2, 3);
        setCell(table.getRow(0).getCell(0), "姓名");
        setCell(table.getRow(0).getCell(1), "中心");
        setCell(table.getRow(0).getCell(2), "平均耗时");
        XWPFTableRow prototype = table.getRow(1);
        setSplitCell(prototype.getCell(0), "{{row:", "personName}}");
        setCell(prototype.getCell(1), "{{row:centerName}}");
        setCell(prototype.getCell(2), "{{row:avgHours|number:0.00}}");

        XWPFParagraph chart = document.createParagraph();
        chart.setAlignment(ParagraphAlignment.CENTER);
        chart.createRun().setText("{{chart:centerEventChart}}");

        centered(document, "页眉页脚与模板样式保留标记", 9);
        return document;
    }

    private static void createPageSettings(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        CTPageSz page = section.addNewPgSz();
        page.setW(BigInteger.valueOf(11906L));
        page.setH(BigInteger.valueOf(16838L));
        CTPageMar margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440L));
        margins.setRight(BigInteger.valueOf(1440L));
        margins.setBottom(BigInteger.valueOf(1440L));
        margins.setLeft(BigInteger.valueOf(1440L));
        margins.setHeader(BigInteger.valueOf(720L));
        margins.setFooter(BigInteger.valueOf(720L));
        margins.setGutter(BigInteger.ZERO);
    }

    private static void createStyles(XWPFDocument document) {
        XWPFStyles styles = document.createStyles();
        for (int level = 1; level <= 4; level++) {
            CTStyle style = CTStyle.Factory.newInstance();
            style.setStyleId("Heading" + level);
            style.setType(STStyleType.PARAGRAPH);
            style.addNewName().setVal("Heading " + level);
            style.addNewBasedOn().setVal("Normal");
            style.addNewNext().setVal("Normal");
            CTPPrGeneral paragraph = style.addNewPPr();
            CTSpacing spacing = paragraph.addNewSpacing();
            spacing.setBefore(BigInteger.valueOf(320L - level * 40L));
            spacing.setAfter(BigInteger.valueOf(160L - level * 20L));
            paragraph.addNewKeepNext();
            CTRPr run = style.addNewRPr();
            run.addNewB();
            CTFonts fonts = run.addNewRFonts();
            fonts.setAscii("SimSun");
            fonts.setHAnsi("SimSun");
            fonts.setEastAsia("SimSun");
            CTHpsMeasure size = run.addNewSz();
            size.setVal(BigInteger.valueOf(34L - level * 4L));
            run.addNewSzCs().setVal(size.getVal());
            styles.addStyle(new XWPFStyle(style));
        }
    }

    private static void centered(
            XWPFDocument document, String text, int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("SimSun");
        run.setFontSize(fontSize);
        run.setText(text);
    }

    private static void pageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setPageBreak(true);
    }

    private static void setCell(XWPFTableCell cell, String text) {
        clear(cell);
        cell.getParagraphs().get(0).createRun().setText(text);
    }

    private static void setSplitCell(
            XWPFTableCell cell, String first, String second) {
        clear(cell);
        cell.getParagraphs().get(0).createRun().setText(first);
        cell.getParagraphs().get(0).createRun().setText(second);
    }

    private static void clear(XWPFTableCell cell) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) {
            paragraph.removeRun(index);
        }
    }
}
