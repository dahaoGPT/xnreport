package com.xn.report.word;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;

/**
 * Word 模板加载与先验完整性校验器。
 * <p>
 * 从磁盘读取 docx 模板并预检：
 * <ul>
 *   <li>样式库完整性：必须预置 Heading1、Heading2、Heading3、Heading4 样式。</li>
 *   <li>TOC 目录域：必须包含且仅包含一个结构合规的 TOC 目录域。</li>
 *   <li>动态章节锚点：必须包含且仅包含一个独立的 <code>{{sections}}</code> 正文段落。</li>
 * </ul>
 * </p>
 */
public final class WordTemplateLoader {

    private final WordTocManager tocManager;
    private final WordSectionAnchorLocator sectionAnchorLocator;

    public WordTemplateLoader() {
        this(new WordRunTextReplacer(), new WordTocManager());
    }

    WordTemplateLoader(
            WordRunTextReplacer replacer, WordTocManager tocManager) {
        this.tocManager = tocManager;
        this.sectionAnchorLocator = new WordSectionAnchorLocator(replacer);
    }

    /**
     * 读取并加载 Word 模板文件，自动触发先验规则校验。
     *
     * @param template 模板文件绝对路径
     * @return XWPFDocument 实例
     * @throws WordTemplateException 若文件不存在或先验校验失败
     */
    public XWPFDocument load(Path template) {
        Path path = requireTemplate(template);
        try {
            InputStream input = Files.newInputStream(path);
            try {
                XWPFDocument document = new XWPFDocument(input);
                try {
                    validate(document);
                    return document;
                } catch (RuntimeException ex) {
                    document.close();
                    throw ex;
                }
            } finally {
                input.close();
            }
        } catch (IOException ex) {
            throw new WordTemplateException(
                    "Unable to read Word template: " + path, ex);
        }
    }

    /**
     * 校验模板文档的结构合规性。
     *
     * @param document 待校验文档
     */
    public void validate(XWPFDocument document) {
        XWPFStyles styles = document.getStyles();
        for (int level = 1; level <= 4; level++) {
            String styleId = "Heading" + level;
            XWPFStyle style = styles == null ? null : styles.getStyle(styleId);
            if (style == null) {
                throw new WordTemplateException(
                        "Word template is missing required Heading "
                                + level + " style (" + styleId + ")");
            }
        }
        tocManager.validate(document);
        sectionAnchorLocator.locate(document);
    }

    private static Path requireTemplate(Path template) {
        if (template == null) {
            throw new IllegalArgumentException("Word template path is required");
        }
        Path path = template.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new WordTemplateException(
                    "Word template does not exist or is not readable: " + path);
        }
        return path;
    }
}
