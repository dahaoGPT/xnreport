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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSdtBlock;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

class WordTocManagerTest {

    private final WordTocManager manager = new WordTocManager();

    @Test
    void updatesRealComplexTocFieldAndRequestsUpdateOnOpen() throws Exception {
        XWPFDocument document = new XWPFDocument();
        addComplexToc(document);

        manager.configure(document, 4, true);

        assertThat(document.getDocument().xmlText())
                .contains("TOC \\o \"1-4\" \\h \\z \\u");
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

    @Test
    void rewritesSplitInstructionAcrossParagraphsAndPreservesResult()
            throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph first = paragraph(document);
            fieldChar(first, STFldCharType.BEGIN);
            first.createRun().getCTR().addNewInstrText()
                    .setStringValue(" TO");
            XWPFParagraph second = paragraph(document);
            second.createRun().getCTR().addNewInstrText()
                    .setStringValue("C \\\\o \"1-2\"");
            second.createRun().getCTR().addNewInstrText()
                    .setStringValue(" \\\\h \\\\z \\\\u ");
            fieldChar(second, STFldCharType.SEPARATE);
            XWPFParagraph result = paragraph(document);
            result.createRun().setText("保留的目录结果");
            fieldChar(paragraph(document), STFldCharType.END);

            manager.configure(document, 4, true);

            try (XWPFDocument reopened = roundTrip(document)) {
                assertThat(manager.configuredInstruction(reopened).trim())
                        .isEqualTo(WordTocManager.instruction(4).trim());
                assertThat(reopened.getDocument().xmlText())
                        .contains("保留的目录结果")
                        .doesNotContain("1-2");
            }
        }
    }

    @Test
    void locatesComplexTocInsideStructuredDocumentTag() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            CTSdtBlock block =
                    document.getDocument().getBody().addNewSdt();
            CTP paragraph = block.addNewSdtContent().addNewP();
            XWPFParagraph wrapped = new XWPFParagraph(paragraph, document);
            fieldChar(wrapped, STFldCharType.BEGIN);
            wrapped.createRun().getCTR().addNewInstrText()
                    .setStringValue(" TO");
            wrapped.createRun().getCTR().addNewInstrText()
                    .setStringValue("C \\\\o \"1-3\" \\\\h \\\\z \\\\u ");
            fieldChar(wrapped, STFldCharType.SEPARATE);
            wrapped.createRun().setText("SDT目录结果");
            fieldChar(wrapped, STFldCharType.END);

            manager.configure(document, 2, true);

            try (XWPFDocument reopened = roundTrip(document)) {
                assertThat(manager.configuredInstruction(reopened).trim())
                        .isEqualTo(WordTocManager.instruction(2).trim());
                assertThat(reopened.getDocument().xmlText())
                        .contains("SDT目录结果");
            }
        }
    }

    @Test
    void rejectsMultipleAndUnclosedTocFields() throws Exception {
        try (XWPFDocument multiple = new XWPFDocument()) {
            addComplexToc(multiple);
            addComplexToc(multiple);
            assertThatThrownBy(() -> manager.validate(multiple))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("exactly one");
        }
        try (XWPFDocument malformed = new XWPFDocument()) {
            XWPFParagraph paragraph = malformed.createParagraph();
            fieldChar(paragraph, STFldCharType.BEGIN);
            paragraph.createRun().getCTR().addNewInstrText()
                    .setStringValue(" TOC \\\\o \"1-3\" ");
            assertThatThrownBy(() -> manager.validate(malformed))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("malformed");
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

    private static XWPFParagraph paragraph(XWPFDocument document) {
        return new XWPFParagraph(
                document.getDocument().getBody().addNewP(), document);
    }

    private static void fieldChar(
            XWPFParagraph paragraph, STFldCharType.Enum type) {
        CTFldChar field = paragraph.createRun().getCTR().addNewFldChar();
        field.setFldCharType(type);
    }

    private static XWPFDocument roundTrip(XWPFDocument document)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        return new XWPFDocument(
                new ByteArrayInputStream(output.toByteArray()));
    }
}
