package com.xn.report.word;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;

public final class WordTemplateLoader {

    private final WordRunTextReplacer replacer;
    private final WordTocManager tocManager;

    public WordTemplateLoader() {
        this(new WordRunTextReplacer(), new WordTocManager());
    }

    WordTemplateLoader(
            WordRunTextReplacer replacer, WordTocManager tocManager) {
        this.replacer = replacer;
        this.tocManager = tocManager;
    }

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
        if (replacer.count(document, "{{sections}}") != 1) {
            throw new WordTemplateException(
                    "Word template must contain exactly one {{sections}} anchor");
        }
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
