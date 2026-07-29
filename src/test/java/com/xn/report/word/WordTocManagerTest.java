package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

class WordTocManagerTest {

    private final WordTocManager manager = new WordTocManager();

    @Test
    void updatesRealComplexTocFieldAndRequestsUpdateOnOpen() throws Exception {
        XWPFDocument document = new XWPFDocument();
        addComplexToc(document);

        manager.configure(document, 4, true);

        assertThat(document.getDocument().xmlText())
                .contains("TOC \\\\o \"1-4\" \\\\h \\\\z \\\\u");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        document.close();

        try (XWPFDocument reopened = new XWPFDocument(
                new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(reopened.getSettings().getCTSettings().isSetUpdateFields())
                    .isTrue();
            assertThat(reopened.getSettings().getCTSettings().xmlText())
                    .contains("updateFields");
        }
    }

    @Test
    void rejectsMissingOrMalformedTocField() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("目录");

            assertThatThrownBy(() -> manager.configure(document, 3, true))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("TOC");
        }
    }

    @Test
    void validatesConfiguredLevel() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            addComplexToc(document);
            assertThatThrownBy(() -> manager.configure(document, 5, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1 and 4");
        }
    }

    static void addComplexToc(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun begin = paragraph.createRun();
        CTFldChar beginChar = begin.getCTR().addNewFldChar();
        beginChar.setFldCharType(STFldCharType.BEGIN);
        paragraph.createRun().getCTR().addNewInstrText()
                .setStringValue(" TOC \\\\o \"1-3\" \\\\h \\\\z \\\\u ");
        XWPFRun separate = paragraph.createRun();
        CTFldChar separateChar = separate.getCTR().addNewFldChar();
        separateChar.setFldCharType(STFldCharType.SEPARATE);
        paragraph.createRun().setText("目录将在打开文档时更新");
        XWPFRun end = paragraph.createRun();
        CTFldChar endChar = end.getCTR().addNewFldChar();
        endChar.setFldCharType(STFldCharType.END);
    }
}
