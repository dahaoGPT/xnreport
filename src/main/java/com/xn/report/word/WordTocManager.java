package com.xn.report.word;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

public final class WordTocManager {

    public void validate(XWPFDocument document) {
        requireDocument(document);
        if (locate(document) == null) {
            throw new WordTemplateException(
                    "Word template must contain a real TOC field");
        }
    }

    public void configure(
            XWPFDocument document, int maxLevel, boolean updateOnOpen) {
        requireDocument(document);
        if (maxLevel < 1 || maxLevel > 4) {
            throw new IllegalArgumentException(
                    "TOC max level must be between 1 and 4");
        }
        TocField field = locate(document);
        if (field == null) {
            throw new WordTemplateException(
                    "Word template must contain a real TOC field");
        }
        field.setInstruction(instruction(maxLevel));
        if (updateOnOpen) {
            document.getSettings().setUpdateFields();
        }
    }

    public static String instruction(int maxLevel) {
        return " TOC \\\\o \"1-" + maxLevel + "\" \\\\h \\\\z \\\\u ";
    }

    private TocField locate(XWPFDocument document) {
        for (XWPFParagraph paragraph : paragraphs(document)) {
            for (CTSimpleField field : paragraph.getCTP().getFldSimpleList()) {
                if (isToc(field.getInstr())) {
                    return new SimpleTocField(field);
                }
            }
            CTText instructionText = null;
            boolean begin = false;
            boolean separate = false;
            boolean end = false;
            for (XWPFRun run : paragraph.getRuns()) {
                for (CTText text : run.getCTR().getInstrTextList()) {
                    if (isToc(text.getStringValue())) {
                        instructionText = text;
                    }
                }
                for (CTFldChar fieldChar : run.getCTR().getFldCharList()) {
                    STFldCharType.Enum type = fieldChar.getFldCharType();
                    begin |= type == STFldCharType.BEGIN;
                    separate |= type == STFldCharType.SEPARATE;
                    end |= type == STFldCharType.END;
                }
            }
            if (instructionText != null && begin && separate && end) {
                return new ComplexTocField(instructionText);
            }
        }
        return null;
    }

    private static List<XWPFParagraph> paragraphs(IBody body) {
        List<XWPFParagraph> result = new ArrayList<XWPFParagraph>();
        collectParagraphs(body, result);
        return result;
    }

    private static void collectParagraphs(
            IBody body, List<XWPFParagraph> result) {
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                result.add((XWPFParagraph) element);
            } else if (element instanceof XWPFTable) {
                for (XWPFTableRow row : ((XWPFTable) element).getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        collectParagraphs(cell, result);
                    }
                }
            }
        }
    }

    private static boolean isToc(String instruction) {
        return instruction != null
                && instruction.trim().toUpperCase(Locale.ROOT).startsWith("TOC");
    }

    private static void requireDocument(XWPFDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
    }

    private interface TocField {
        void setInstruction(String instruction);
    }

    private static final class SimpleTocField implements TocField {
        private final CTSimpleField field;

        private SimpleTocField(CTSimpleField field) {
            this.field = field;
        }

        @Override
        public void setInstruction(String instruction) {
            field.setInstr(instruction);
        }
    }

    private static final class ComplexTocField implements TocField {
        private final CTText text;

        private ComplexTocField(CTText text) {
            this.text = text;
        }

        @Override
        public void setInstruction(String instruction) {
            text.setStringValue(instruction);
        }
    }
}
