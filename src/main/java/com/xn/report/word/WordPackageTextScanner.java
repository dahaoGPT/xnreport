package com.xn.report.word;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.xmlbeans.XmlObject;
import org.xml.sax.InputSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class WordPackageTextScanner {

    private WordPackageTextScanner() {
    }

    static int count(XWPFDocument document, String token) {
        int count = count(document.getDocument().getBody(), token);
        for (XWPFHeader header : uniqueHeaders(document)) {
            count += count(header._getHdrFtr(), token);
        }
        for (XWPFFooter footer : uniqueFooters(document)) {
            count += count(footer._getHdrFtr(), token);
        }
        for (XmlObject story : additionalStories(document)) {
            count += count(story, token);
        }
        return count;
    }

    static boolean contains(
            XWPFDocument document, Pattern pattern) {
        for (String text : paragraphTexts(document)) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> paragraphTexts(
            XWPFDocument document) {
        List<String> values = new ArrayList<String>();
        paragraphs(document.getDocument().getBody(), values);
        for (XWPFHeader header : uniqueHeaders(document)) {
            paragraphs(header._getHdrFtr(), values);
        }
        for (XWPFFooter footer : uniqueFooters(document)) {
            paragraphs(footer._getHdrFtr(), values);
        }
        for (XmlObject story : additionalStories(document)) {
            paragraphs(story, values);
        }
        return values;
    }

    private static List<XmlObject> additionalStories(
            XWPFDocument document) {
        List<XmlObject> stories = new ArrayList<XmlObject>();
        List<XWPFFootnote> footnotes = document.getFootnotes();
        if (footnotes != null) {
            for (XWPFFootnote footnote : footnotes) {
                stories.add(footnote.getCTFtnEdn());
            }
        }
        List<XWPFEndnote> endnotes = document.getEndnotes();
        if (endnotes != null) {
            for (XWPFEndnote endnote : endnotes) {
                stories.add(endnote.getCTFtnEdn());
            }
        }
        XWPFComment[] comments = document.getComments();
        if (comments != null) {
            for (XWPFComment comment : comments) {
                stories.add(comment.getCtComment());
            }
        }
        return stories;
    }

    private static int count(XmlObject root, String token) {
        int count = 0;
        List<String> values = new ArrayList<String>();
        paragraphs(root, values);
        for (String value : values) {
            int from = 0;
            while (from <= value.length() - token.length()) {
                int match = value.indexOf(token, from);
                if (match < 0) {
                    break;
                }
                count++;
                from = match + token.length();
            }
        }
        return count;
    }

    private static void paragraphs(
            XmlObject xml, List<String> values) {
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
                    new InputSource(new StringReader(xml.xmlText())))
                    .getDocumentElement();
            paragraphs(root, values);
        } catch (Exception exception) {
            throw new WordTemplateException(
                    "Could not scan Word package text", exception);
        }
    }

    private static void paragraphs(Node node, List<String> values) {
        if (node.getNodeType() == Node.ELEMENT_NODE
                && "p".equals(node.getLocalName())) {
            StringBuilder text = new StringBuilder();
            appendText(node, text);
            values.add(text.toString());
            return;
        }
        for (Node child = node.getFirstChild();
                child != null; child = child.getNextSibling()) {
            paragraphs(child, values);
        }
    }

    private static void appendText(Node node, StringBuilder text) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            if ("t".equals(element.getLocalName())
                    || "instrText".equals(element.getLocalName())) {
                for (Node child = element.getFirstChild();
                        child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == Node.TEXT_NODE
                            || child.getNodeType()
                            == Node.CDATA_SECTION_NODE) {
                        text.append(child.getNodeValue());
                    }
                }
                return;
            }
        }
        for (Node child = node.getFirstChild();
                child != null; child = child.getNextSibling()) {
            appendText(child, text);
        }
    }

    private static Set<XWPFHeader> uniqueHeaders(
            XWPFDocument document) {
        Set<XWPFHeader> values = Collections.newSetFromMap(
                new IdentityHashMap<XWPFHeader, Boolean>());
        values.addAll(document.getHeaderList());
        return values;
    }

    private static Set<XWPFFooter> uniqueFooters(
            XWPFDocument document) {
        Set<XWPFFooter> values = Collections.newSetFromMap(
                new IdentityHashMap<XWPFFooter, Boolean>());
        values.addAll(document.getFooterList());
        return values;
    }
}
