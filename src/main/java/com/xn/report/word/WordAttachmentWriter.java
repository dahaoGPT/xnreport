package com.xn.report.word;

import com.xn.report.config.definition.WordComponentDefinition;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public final class WordAttachmentWriter {

    static final String BOOKMARK_PREFIX = "_XN_ATTACHMENT_";
    private static final String WORD_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    public void append(
            XWPFDocument document, WordComponentDefinition definition) {
        if (document == null || definition == null) {
            throw new IllegalArgumentException(
                    "Word document and attachment definition are required");
        }
        List<XWPFParagraph> paragraphs = new ArrayList<XWPFParagraph>();
        add(paragraphs, appendText(document, title(definition), true));
        add(paragraphs, appendText(
                document, definition.getDescription(), false));
        for (String item : definition.getItems()) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("ListBullet");
            paragraph.createRun().setText(item);
            paragraphs.add(paragraph);
        }
        mark(document, paragraphs);
    }

    void append(
            WordBodyInserter inserter, WordComponentDefinition definition) {
        if (inserter == null || definition == null) {
            throw new IllegalArgumentException(
                    "Word inserter and attachment definition are required");
        }
        List<XWPFParagraph> paragraphs = new ArrayList<XWPFParagraph>();
        add(paragraphs, appendText(inserter, title(definition), true));
        add(paragraphs, appendText(
                inserter, definition.getDescription(), false));
        for (String item : definition.getItems()) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            XWPFParagraph paragraph = inserter.paragraph();
            paragraph.setStyle("ListBullet");
            paragraph.createRun().setText(item);
            paragraphs.add(paragraph);
        }
        mark(inserter.document(), paragraphs);
    }

    static XWPFParagraph appendText(
            XWPFDocument document, String text, boolean bold) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setBold(bold);
        paragraph.getRuns().get(0).setText(text);
        return paragraph;
    }

    private static XWPFParagraph appendText(
            WordBodyInserter inserter, String text, boolean bold) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        XWPFParagraph paragraph = inserter.paragraph();
        paragraph.createRun().setBold(bold);
        paragraph.getRuns().get(0).setText(text);
        return paragraph;
    }

    private static void add(
            List<XWPFParagraph> paragraphs, XWPFParagraph paragraph) {
        if (paragraph != null) {
            paragraphs.add(paragraph);
        }
    }

    private static void mark(
            XWPFDocument document, List<XWPFParagraph> paragraphs) {
        if (paragraphs.isEmpty()) {
            return;
        }
        BigInteger id = nextBookmarkId(document);
        XWPFParagraph first = paragraphs.get(0);
        CTBookmark start = first.getCTP().addNewBookmarkStart();
        start.setId(id);
        start.setName(BOOKMARK_PREFIX + id.toString(36));
        first.getCTP().addNewBookmarkEnd().setId(id);
    }

    private static BigInteger nextBookmarkId(XWPFDocument document) {
        Set<BigInteger> used = new HashSet<BigInteger>();
        collectBookmarkIds(document.getDocument().getBody(), used);
        for (XWPFHeader header : document.getHeaderList()) {
            collectBookmarkIds(header._getHdrFtr(), used);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            collectBookmarkIds(footer._getHdrFtr(), used);
        }
        for (POIXMLDocumentPart relation : document.getRelations()) {
            if (relation instanceof XWPFHeader) {
                collectBookmarkIds(
                        ((XWPFHeader) relation)._getHdrFtr(), used);
            } else if (relation instanceof XWPFFooter) {
                collectBookmarkIds(
                        ((XWPFFooter) relation)._getHdrFtr(), used);
            }
        }
        BigInteger candidate = BigInteger.ONE;
        while (used.contains(candidate)) {
            candidate = candidate.add(BigInteger.ONE);
        }
        return candidate;
    }

    private static void collectBookmarkIds(
            XmlObject story, Set<BigInteger> used) {
        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);
            Node root = factory.newDocumentBuilder().parse(
                    new InputSource(new StringReader(story.xmlText())))
                    .getDocumentElement();
            collectBookmarkIds(root, used);
        } catch (Exception exception) {
            throw new WordTemplateException(
                    "Could not allocate a Word attachment bookmark ID",
                    exception);
        }
    }

    private static void collectBookmarkIds(
            Node node, Set<BigInteger> used) {
        if (node.getNodeType() == Node.ELEMENT_NODE
                && "bookmarkStart".equals(node.getLocalName())
                && WORD_NAMESPACE.equals(node.getNamespaceURI())) {
            String value = ((Element) node).getAttributeNS(
                    WORD_NAMESPACE, "id");
            if (value != null && !value.trim().isEmpty()) {
                used.add(new BigInteger(value));
            }
        }
        for (Node child = node.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            collectBookmarkIds(child, used);
        }
    }

    private static String title(WordComponentDefinition definition) {
        return definition.getTitle() == null
                || definition.getTitle().trim().isEmpty()
                ? definition.getText() : definition.getTitle();
    }
}
