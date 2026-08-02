package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.WordComponentDefinition;
import java.util.Arrays;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;

class WordAttachmentWriterTest {

    @Test
    void writesAttachmentTitleDescriptionAndItemsInConfiguredOrder()
            throws Exception {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setTitle("附件信息");
        component.setDescription("本报告包含以下附件：");
        component.setItems(Arrays.asList("人员明细.xlsx", "规则说明.pdf"));

        try (XWPFDocument document = new XWPFDocument()) {
            new WordAttachmentWriter().append(document, component);
            assertThat(document.getParagraphs().get(0).getCTP()
                    .getBookmarkStartList())
                    .anySatisfy(bookmark -> assertThat(bookmark.getName())
                            .startsWith("_XN_ATTACHMENT_"));
            BigInteger markerId = document.getParagraphs().get(0).getCTP()
                    .getBookmarkStartList().get(0).getId();
            assertThat(document.getParagraphs().get(0).getCTP()
                    .getBookmarkEndList())
                    .anySatisfy(bookmark -> assertThat(bookmark.getId())
                            .isEqualTo(markerId));
            assertThat(document.getParagraphs())
                    .extracting(paragraph -> paragraph.getStyle())
                    .containsExactly(
                            null, null, "ListBullet", "ListBullet");
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                String text = extractor.getText();
                assertThat(text.indexOf("附件信息"))
                        .isLessThan(text.indexOf("本报告包含以下附件："));
                assertThat(text.indexOf("本报告包含以下附件："))
                        .isLessThan(text.indexOf("人员明细.xlsx"));
                assertThat(text.indexOf("人员明细.xlsx"))
                        .isLessThan(text.indexOf("规则说明.pdf"));
            }
        }
    }

    @Test
    void inserterPathWritesTheSameAttachmentBookmarkContract()
            throws Exception {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setTitle("Attachment");
        component.setDescription("Description");
        try (XWPFDocument document = new XWPFDocument()) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph anchor =
                    document.createParagraph();
            anchor.createRun().setText("anchor");
            new WordAttachmentWriter().append(
                    new WordBodyInserter(document, anchor), component);

            assertThat(document.getParagraphs().get(0).getCTP()
                    .getBookmarkStartList())
                    .anySatisfy(bookmark -> assertThat(bookmark.getName())
                            .startsWith("_XN_ATTACHMENT_"));
            assertThat(document.getParagraphs().get(0).getCTP()
                    .getBookmarkEndList()).hasSize(1);
        }
    }
}
