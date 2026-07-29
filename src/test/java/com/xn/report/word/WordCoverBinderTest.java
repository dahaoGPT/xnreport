package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.WordCoverDefinition;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class WordCoverBinderTest {

    private final WordCoverBinder binder =
            new WordCoverBinder(new WordRunTextReplacer());

    @Test
    void bindsAllFiveRequiredCoverVariables() throws Exception {
        try (XWPFDocument document = coverTemplate()) {
            WordCoverDefinition cover = new WordCoverDefinition();
            cover.setTitle("研发效能报告");
            cover.setOrganization("软件开发二中心");
            cover.setReportPeriod("2026年6月");
            cover.setPreparedBy("效能小组");
            cover.setPreparedDate("2026年7月23日");

            binder.bind(document, cover);

            assertThat(document.getParagraphs())
                    .extracting(paragraph -> paragraph.getStyle())
                    .containsExactly(
                            "Title", "Subtitle", "CoverPeriod",
                            "CoverAuthor", "CoverDate");
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                assertThat(extractor.getText())
                        .contains("研发效能报告")
                        .contains("软件开发二中心")
                        .contains("2026年6月")
                        .contains("效能小组")
                        .contains("2026年7月23日")
                        .doesNotContain("{{");
            }
        }
    }

    @Test
    void failsClearlyWhenRequiredCoverPlaceholderIsMissing() throws Exception {
        try (XWPFDocument document = coverTemplate()) {
            document.removeBodyElement(document.getBodyElements().size() - 1);
            assertThatThrownBy(() -> binder.bind(document, completeCover()))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("preparedDate");
        }
    }

    private static XWPFDocument coverTemplate() {
        XWPFDocument document = new XWPFDocument();
        String[] styles = {
            "Title", "Subtitle", "CoverPeriod", "CoverAuthor", "CoverDate"
        };
        String[] tokens = {
            "{{cover:title}}", "{{cover:organization}}",
            "{{cover:reportPeriod}}", "{{cover:preparedBy}}",
            "{{cover:preparedDate}}"
        };
        for (int index = 0; index < tokens.length; index++) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph =
                    document.createParagraph();
            paragraph.setStyle(styles[index]);
            paragraph.createRun().setText(tokens[index]);
        }
        return document;
    }

    private static WordCoverDefinition completeCover() {
        WordCoverDefinition cover = new WordCoverDefinition();
        cover.setTitle("title");
        cover.setOrganization("organization");
        cover.setReportPeriod("period");
        cover.setPreparedBy("author");
        cover.setPreparedDate("date");
        return cover;
    }
}
