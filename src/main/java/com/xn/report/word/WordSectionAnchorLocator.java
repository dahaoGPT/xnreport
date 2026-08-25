package com.xn.report.word;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Word 动态章节插入锚点（<code>{{sections}}</code>）定位器。
 * <p>
 * 严格遵循规范要求：<code>{{sections}}</code> 占位符必须且仅能作为独立的顶级正文段落出现一次。
 * </p>
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

    /**
     * 在文档中精确定位唯一的动态章节锚点段落。
     *
     * @param document 目标 Word 文档
     * @return 锚点段落对象
     * @throws WordTemplateException 若出现次数不为 1 或非独立段落
     */
    public XWPFParagraph locate(XWPFDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
        if (WordPackageTextScanner.count(document, TOKEN) != 1) {
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
