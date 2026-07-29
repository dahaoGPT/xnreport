package com.xn.report.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

final class WordBodyInserter {

    private final XWPFDocument document;
    private final XmlObject anchor;

    WordBodyInserter(XWPFDocument document, XWPFParagraph anchor) {
        this.document = document;
        this.anchor = anchor.getCTP();
    }

    XWPFParagraph paragraph() {
        XmlCursor cursor = beforeAnchor();
        try {
            XWPFParagraph paragraph = document.insertNewParagraph(cursor);
            return paragraph;
        } finally {
            cursor.dispose();
        }
    }

    XWPFTable table() {
        XmlCursor cursor = beforeAnchor();
        try {
            XWPFTable table = document.insertNewTbl(cursor);
            return table;
        } finally {
            cursor.dispose();
        }
    }

    private XmlCursor beforeAnchor() {
        return anchor.newCursor();
    }
}
