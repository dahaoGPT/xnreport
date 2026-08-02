package com.xn.report.word;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Locates a real Word TOC field in document order. Complex fields are parsed
 * as a stream, so their begin/instruction/separate/end nodes may be split
 * across runs, paragraphs, or structured document tags.
 */
public final class WordTocManager {

    private static final String WORD_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    public void validate(XWPFDocument document) {
        requireDocument(document);
        locateExactlyOne(document);
    }

    public void configure(
            XWPFDocument document, int maxLevel, boolean updateOnOpen) {
        requireDocument(document);
        if (maxLevel < 1 || maxLevel > 4) {
            throw new IllegalArgumentException(
                    "TOC max level must be between 1 and 4");
        }
        locateExactlyOne(document).setInstruction(instruction(maxLevel));
        if (updateOnOpen) {
            document.getSettings().setUpdateFields();
        }
    }

    public static String instruction(int maxLevel) {
        return " TOC \\o \"1-" + maxLevel + "\" \\h \\z \\u ";
    }

    String configuredInstruction(XWPFDocument document) {
        requireDocument(document);
        return locateExactlyOne(document).getInstruction();
    }

    private TocField locateExactlyOne(XWPFDocument document) {
        FieldScan scan = new FieldScan();
        scan.visit(document.getDocument().getBody().getDomNode());
        scan.finish();
        if (scan.malformed) {
            throw new WordTemplateException(
                    "Word template contains a malformed TOC field");
        }
        if (scan.tocFields.size() != 1) {
            throw new WordTemplateException(
                    "Word template must contain exactly one real TOC field; found "
                            + scan.tocFields.size());
        }
        return scan.tocFields.get(0);
    }

    private static boolean isToc(String value) {
        return value != null
                && value.trim().toUpperCase(Locale.ROOT).startsWith("TOC");
    }

    private static void requireDocument(XWPFDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
    }

    private interface TocField {
        void setInstruction(String instruction);

        String getInstruction();
    }

    private static final class SimpleTocField implements TocField {
        private final Element field;

        private SimpleTocField(Element field) {
            this.field = field;
        }

        @Override
        public void setInstruction(String instruction) {
            field.setAttributeNS(WORD_NS, "w:instr", instruction);
        }

        @Override
        public String getInstruction() {
            return field.getAttributeNS(WORD_NS, "instr");
        }
    }

    private static final class ComplexTocField implements TocField {
        private final List<Element> instructionNodes;

        private ComplexTocField(List<Element> instructionNodes) {
            this.instructionNodes =
                    new ArrayList<Element>(instructionNodes);
        }

        @Override
        public void setInstruction(String instruction) {
            setElementText(instructionNodes.get(0), instruction);
            for (int index = 1; index < instructionNodes.size(); index++) {
                setElementText(instructionNodes.get(index), "");
            }
        }

        @Override
        public String getInstruction() {
            StringBuilder value = new StringBuilder();
            for (Element node : instructionNodes) {
                value.append(elementText(node));
            }
            return value.toString();
        }
    }

    private static final class ComplexField {
        private final List<Element> instructionNodes =
                new ArrayList<Element>();
        private final StringBuilder instruction = new StringBuilder();
        private boolean separated;

        private void addInstruction(Element node) {
            if (!separated) {
                instructionNodes.add(node);
                instruction.append(elementText(node));
            }
        }
    }

    private static final class FieldScan {
        private final Deque<ComplexField> stack =
                new ArrayDeque<ComplexField>();
        private final List<TocField> tocFields =
                new ArrayList<TocField>();
        private boolean malformed;

        private void visit(Node node) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String local = element.getLocalName();
                if ("fldSimple".equals(local)) {
                    String value = element.getAttributeNS(WORD_NS, "instr");
                    if (isToc(value)) {
                        tocFields.add(new SimpleTocField(element));
                    }
                    return;
                }
                if ("fldChar".equals(local)) {
                    fieldChar(element.getAttributeNS(
                            WORD_NS, "fldCharType"));
                } else if ("instrText".equals(local)) {
                    instruction(element);
                }
            }
            for (Node child = node.getFirstChild();
                    child != null; child = child.getNextSibling()) {
                visit(child);
            }
        }

        private void fieldChar(String type) {
            if ("begin".equals(type)) {
                stack.push(new ComplexField());
                return;
            }
            if ("separate".equals(type)) {
                if (stack.isEmpty()) {
                    malformed = true;
                } else {
                    stack.peek().separated = true;
                }
                return;
            }
            if ("end".equals(type)) {
                if (stack.isEmpty()) {
                    malformed = true;
                    return;
                }
                ComplexField completed = stack.pop();
                if (isToc(completed.instruction.toString())) {
                    if (!completed.separated
                            || completed.instructionNodes.isEmpty()) {
                        malformed = true;
                    } else {
                        tocFields.add(new ComplexTocField(
                                completed.instructionNodes));
                    }
                }
            }
        }

        private void instruction(Element element) {
            if (stack.isEmpty()) {
                if (isToc(elementText(element))) {
                    malformed = true;
                }
                return;
            }
            stack.peek().addInstruction(element);
        }

        private void finish() {
            while (!stack.isEmpty()) {
                ComplexField field = stack.pop();
                if (isToc(field.instruction.toString())) {
                    malformed = true;
                }
            }
        }
    }

    private static String elementText(Element element) {
        StringBuilder value = new StringBuilder();
        for (Node child = element.getFirstChild();
                child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                value.append(child.getNodeValue());
            }
        }
        return value.toString();
    }

    private static void setElementText(Element element, String value) {
        Node text = element.getFirstChild();
        if (text == null) {
            element.appendChild(
                    element.getOwnerDocument().createTextNode(value));
            return;
        }
        text.setNodeValue(value);
        while (text.getNextSibling() != null) {
            element.removeChild(text.getNextSibling());
        }
    }
}
