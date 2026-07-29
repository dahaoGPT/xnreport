package com.xn.report.word;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Applies the single definition of a valid dynamic-section insertion point.
 */
public final class WordSectionAnchorLocator {

    static final String TOKEN = "{{sections}}";
    private static final String INVALID_MESSAGE =
            "{{sections}} must appear exactly once as a standalone"
                    + " top-level body paragraph";

    private final WordRunTextReplacer replacer;

    public WordSectionAnchorLocator() {
        this(new WordRunTextReplacer());
    }

    WordSectionAnchorLocator(WordRunTextReplacer replacer) {
        if (replacer == null) {
            throw new IllegalArgumentException(
                    "Word text replacer is required");
        }
        this.replacer = replacer;
    }

    public XWPFParagraph locate(XWPFDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
        if (replacer.count(document, TOKEN) != 1) {
            throw new WordTemplateException(INVALID_MESSAGE);
        }
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                if (TOKEN.equals(paragraph.getText().trim())) {
                    return paragraph;
                }
            }
        }
        throw new WordTemplateException(INVALID_MESSAGE);
    }
}
