package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.WordCoverDefinition;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

class WordTemplateFidelityTest {

    @TempDir
    Path tempDir;

    @Test
    void bindingAndSavingPreservesTemplatePackagePartsAndRelationships()
            throws Exception {
        Path source = Paths.get(
                "src/test/resources/fixtures/templates/report-template.docx");
        Path working = tempDir.resolve("working.docx");
        Path output = tempDir.resolve("output.docx");
        Files.copy(source, working);

        TemplateInvariant before;
        try (XWPFDocument document = new WordTemplateLoader().load(working)) {
            before = TemplateInvariant.capture(document);
            WordCoverDefinition cover = new WordCoverDefinition();
            cover.setTitle("研发效能报告");
            cover.setOrganization("软件开发二中心");
            cover.setReportPeriod("2026年6月");
            cover.setPreparedBy("效能小组");
            cover.setPreparedDate("2026年7月23日");
            new WordCoverBinder(new WordRunTextReplacer())
                    .bind(document, cover);
            document.createParagraph().createRun()
                    .setText("运行时新增正文，模板部件必须保留");
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument reopened = new XWPFDocument(stream)) {
            TemplateInvariant after = TemplateInvariant.capture(reopened);
            assertThat(reopened.getHeaderList()).hasSize(1);
            assertThat(reopened.getHeaderList().get(0).getText())
                    .contains("模板页眉标记", "软件开发二中心");
            assertThat(reopened.getFooterList()).hasSize(1);
            assertThat(reopened.getFooterList().get(0).getText())
                    .contains("模板页脚标记", "2026年7月23日");
            assertThat(after.themeSemantic)
                    .isEqualTo(before.themeSemantic);
            assertThat(after.numberingXml).isEqualTo(before.numberingXml);
            assertThat(after.pageWidth).isEqualTo(before.pageWidth);
            assertThat(after.pageHeight).isEqualTo(before.pageHeight);
            assertThat(after.marginsXml).isEqualTo(before.marginsXml);
            assertThat(after.styleXml).isEqualTo(before.styleXml);
            assertThat(after.relationshipCounts)
                    .isEqualTo(before.relationshipCounts);
            assertThat(reopened.getStyles().getStyle("ReportBody"))
                    .isNotNull();
            assertThat(reopened.getNumbering().getAbstractNums())
                    .isNotEmpty();
            assertThat(reopened.getNumbering().getNums()).isNotEmpty();
        }
    }

    private static final class TemplateInvariant {
        private final String themeSemantic;
        private final String numberingXml;
        private final BigInteger pageWidth;
        private final BigInteger pageHeight;
        private final String marginsXml;
        private final String styleXml;
        private final Map<String, Integer> relationshipCounts;

        private TemplateInvariant(
                String themeSemantic,
                String numberingXml,
                BigInteger pageWidth,
                BigInteger pageHeight,
                String marginsXml,
                String styleXml,
                Map<String, Integer> relationshipCounts) {
            this.themeSemantic = themeSemantic;
            this.numberingXml = numberingXml;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.marginsXml = marginsXml;
            this.styleXml = styleXml;
            this.relationshipCounts = relationshipCounts;
        }

        static TemplateInvariant capture(XWPFDocument document)
                throws Exception {
            String themeSemantic =
                    semantic(document.getTheme().getPackagePart());
            String numberingXml = document.getNumbering()
                    .getAbstractNums().get(0).getCTAbstractNum().xmlText()
                    + document.getNumbering().getNums().get(0)
                    .getCTNum().xmlText();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr
                    section = document.getDocument().getBody().getSectPr();
            Map<String, Integer> relationships =
                    new LinkedHashMap<String, Integer>();
            relationships.put("header", count(document,
                    XWPFRelation.HEADER.getRelation()));
            relationships.put("footer", count(document,
                    XWPFRelation.FOOTER.getRelation()));
            relationships.put("theme", count(document,
                    XWPFRelation.THEME.getRelation()));
            relationships.put("numbering", count(document,
                    XWPFRelation.NUMBERING.getRelation()));
            return new TemplateInvariant(
                    themeSemantic,
                    numberingXml,
                    (BigInteger) section.getPgSz().getW(),
                    (BigInteger) section.getPgSz().getH(),
                    section.getPgMar().xmlText(),
                    document.getStyles().getStyle("Heading1")
                            .getCTStyle().xmlText()
                            + document.getStyles().getStyle("ReportBody")
                            .getCTStyle().xmlText(),
                    relationships);
        }

        private static int count(XWPFDocument document, String type)
                throws Exception {
            PackageRelationshipCollection relationships =
                    document.getPackagePart().getRelationshipsByType(type);
            return relationships.size();
        }

        private static String semantic(PackagePart part) throws Exception {
            try (InputStream input = part.getInputStream()) {
                javax.xml.parsers.DocumentBuilderFactory factory =
                        javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Node root = factory.newDocumentBuilder()
                        .parse(input).getDocumentElement();
                StringBuilder result = new StringBuilder();
                appendSemantic(root, result);
                return result.toString();
            }
        }

        private static void appendSemantic(
                Node node, StringBuilder result) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.append('<').append(node.getNamespaceURI())
                        .append('|').append(node.getLocalName());
                NamedNodeMap attributes = node.getAttributes();
                List<String> values = new ArrayList<String>();
                for (int index = 0; index < attributes.getLength(); index++) {
                    Node attribute = attributes.item(index);
                    if (!"http://www.w3.org/2000/xmlns/"
                            .equals(attribute.getNamespaceURI())) {
                        values.add(attribute.getNamespaceURI() + "|"
                                + attribute.getLocalName() + "="
                                + attribute.getNodeValue());
                    }
                }
                Collections.sort(values);
                for (String value : values) {
                    result.append('|').append(value);
                }
                result.append('>');
            } else if (node.getNodeType() == Node.TEXT_NODE
                    && !node.getNodeValue().trim().isEmpty()) {
                result.append(node.getNodeValue().trim());
            }
            for (Node child = node.getFirstChild();
                    child != null; child = child.getNextSibling()) {
                appendSemantic(child, result);
            }
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.append("</").append(node.getNamespaceURI())
                        .append('|').append(node.getLocalName()).append('>');
            }
        }
    }
}
