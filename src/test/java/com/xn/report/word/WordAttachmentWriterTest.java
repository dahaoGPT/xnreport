package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.WordComponentDefinition;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

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

    @Test
    void documentPathAvoidsBookmarkIdsUsedAcrossDocumentStories()
            throws Exception {
        BigInteger collisionId = nextLegacyGeneratedId();
        try (XWPFDocument document = new XWPFDocument()) {
            List<BigInteger> existingIds = preloadBookmarkIds(
                    document, collisionId);

            new WordAttachmentWriter().append(document, attachment());

            assertThat(attachmentMarkerId(document))
                    .isNotIn(existingIds);
        }
    }

    @Test
    void inserterPathAvoidsBookmarkIdsUsedAcrossDocumentStories()
            throws Exception {
        BigInteger collisionId = nextLegacyGeneratedId();
        try (XWPFDocument document = new XWPFDocument()) {
            List<BigInteger> existingIds = preloadBookmarkIds(
                    document, collisionId);
            XWPFParagraph anchor = document.createParagraph();
            anchor.createRun().setText("anchor");

            new WordAttachmentWriter().append(
                    new WordBodyInserter(document, anchor), attachment());

            assertThat(attachmentMarkerId(document))
                    .isNotIn(existingIds);
        }
    }

    private static BigInteger nextLegacyGeneratedId() throws Exception {
        try (XWPFDocument probe = new XWPFDocument()) {
            new WordAttachmentWriter().append(probe, attachment());
            return attachmentMarkerId(probe).add(BigInteger.ONE);
        }
    }

    private static List<BigInteger> preloadBookmarkIds(
            XWPFDocument document, BigInteger collisionId) {
        List<BigInteger> ids = new ArrayList<BigInteger>();
        XWPFHeaderFooterPolicy policy =
                new XWPFHeaderFooterPolicy(document);
        XWPFHeader header = policy.createHeader(
                XWPFHeaderFooterPolicy.DEFAULT);
        for (BigInteger id = BigInteger.ONE;
                id.compareTo(collisionId) < 0;
                id = id.add(BigInteger.ONE)) {
            addBookmark(header.createParagraph(), id, "header_" + id);
            ids.add(id);
        }
        addBookmark(document.createParagraph(), collisionId,
                "body_collision");
        ids.add(collisionId);
        BigInteger footerId = collisionId.add(BigInteger.ONE);
        XWPFFooter footer = policy.createFooter(
                XWPFHeaderFooterPolicy.DEFAULT);
        addBookmark(footer.createParagraph(), footerId,
                "footer_" + footerId);
        ids.add(footerId);
        return ids;
    }

    private static void addBookmark(
            XWPFParagraph paragraph, BigInteger id, String name) {
        paragraph.getCTP().addNewBookmarkStart().setId(id);
        paragraph.getCTP().getBookmarkStartList().get(0).setName(name);
        paragraph.getCTP().addNewBookmarkEnd().setId(id);
    }

    private static BigInteger attachmentMarkerId(XWPFDocument document) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark
                    bookmark : paragraph.getCTP().getBookmarkStartList()) {
                if (bookmark.getName() != null
                        && bookmark.getName().startsWith(
                        WordAttachmentWriter.BOOKMARK_PREFIX)) {
                    return bookmark.getId();
                }
            }
        }
        throw new AssertionError("Attachment bookmark marker is missing");
    }

    private static WordComponentDefinition attachment() {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setTitle("Attachment");
        component.setDescription("Description");
        return component;
    }
}
