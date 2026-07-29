package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class WordRunTextReplacerTest {

    private final WordRunTextReplacer replacer = new WordRunTextReplacer();

    @Test
    void replacesPlaceholderSplitAcrossRunsWithoutChangingSurroundingRunStyles()
            throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("前缀 ");
            paragraph.createRun().setText("{{cover:");
            paragraph.getRuns().get(1).setBold(true);
            paragraph.createRun().setText("title}}");
            paragraph.getRuns().get(2).setItalic(true);
            paragraph.createRun().setText(" 后缀");

            int count = replacer.replace(
                    document, "{{cover:title}}", "研发效能报告");

            assertThat(count).isEqualTo(1);
            assertThat(paragraph.getText()).isEqualTo("前缀 研发效能报告 后缀");
            assertThat(paragraph.getRuns().get(1).isBold()).isTrue();
            assertThat(paragraph.getRuns().get(2).isItalic()).isTrue();
            assertThat(paragraph.getRuns().get(3).text()).isEqualTo(" 后缀");
        }
    }

    @Test
    void replacesBodyTableHeaderAndFooterPlaceholders() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("{{value}}");
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("{{value}}");
            XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
            XWPFHeader header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);
            header.createParagraph().createRun().setText("{{value}}");
            XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
            footer.createParagraph().createRun().setText("{{value}}");

            int count = replacer.replaceAll(
                    document, Collections.singletonMap("{{value}}", "已替换"));

            assertThat(count).isEqualTo(4);
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                assertThat(extractor.getText()).doesNotContain("{{value}}");
            }
            assertThat(header.getText()).contains("已替换");
            assertThat(footer.getText()).contains("已替换");
        }
    }
}
