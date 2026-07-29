package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.WordCoverDefinition;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
            assertThat(after.themeHash).isEqualTo(before.themeHash);
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
        private final String themeHash;
        private final String numberingXml;
        private final BigInteger pageWidth;
        private final BigInteger pageHeight;
        private final String marginsXml;
        private final String styleXml;
        private final Map<String, Integer> relationshipCounts;

        private TemplateInvariant(
                String themeHash,
                String numberingXml,
                BigInteger pageWidth,
                BigInteger pageHeight,
                String marginsXml,
                String styleXml,
                Map<String, Integer> relationshipCounts) {
            this.themeHash = themeHash;
            this.numberingXml = numberingXml;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.marginsXml = marginsXml;
            this.styleXml = styleXml;
            this.relationshipCounts = relationshipCounts;
        }

        static TemplateInvariant capture(XWPFDocument document)
                throws Exception {
            String themeHash = hash(document.getTheme().getPackagePart());
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
                    themeHash,
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

        private static String hash(PackagePart part) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = part.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        }
    }
}
