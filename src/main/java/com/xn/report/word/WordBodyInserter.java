package com.xn.report.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

/**
 * Word 正文指定锚点位置元素插入器（包级私有工具）。
 * <p>
 * 利用 POI 底层 {@link XmlCursor} 在指定锚点段落（如 <code>{{sections}}</code>）之前精准插入新段落或新表格。
 * </p>
 */
final class WordBodyInserter {

    private final XWPFDocument document;
    private final XmlObject anchor;

    WordBodyInserter(XWPFDocument document, XWPFParagraph anchor) {
        this.document = document;
        this.anchor = anchor.getCTP();
    }

    /**
     * 在锚点前插入新段落。
     *
     * @return 新建的 XWPFParagraph 实例
     */
    XWPFParagraph paragraph() {
        XmlCursor cursor = beforeAnchor();
        try {
            XWPFParagraph paragraph = document.insertNewParagraph(cursor);
            return paragraph;
        } finally {
            cursor.dispose();
        }
    }

    /**
     * 在锚点前插入新表格。
     *
     * @return 新建的 XWPFTable 实例
     */
    XWPFTable table() {
        XmlCursor cursor = beforeAnchor();
        try {
            XWPFTable table = document.insertNewTbl(cursor);
            return table;
        } finally {
            cursor.dispose();
        }
    }

    XWPFDocument document() {
        return document;
    }

    private XmlCursor beforeAnchor() {
        return anchor.newCursor();
    }
}
