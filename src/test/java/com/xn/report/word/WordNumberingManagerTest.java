package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.WordNumberingDefinition;
import com.xn.report.config.definition.WordNumberingLevelDefinition;
import java.math.BigInteger;
import java.util.Arrays;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.junit.jupiter.api.Test;

class WordNumberingManagerTest {

    @Test
    void createsOneReusableFourLevelHeadingNumberingDefinition() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            WordNumberingManager manager = new WordNumberingManager(document);
            XWPFParagraph first = document.createParagraph();
            XWPFParagraph second = document.createParagraph();

            BigInteger firstId = manager.apply(first, 1);
            BigInteger secondId = manager.apply(second, 4);

            assertThat(firstId).isEqualTo(secondId);
            assertThat(first.getNumIlvl()).isEqualTo(BigInteger.ZERO);
            assertThat(second.getNumIlvl()).isEqualTo(BigInteger.valueOf(3));
            BigInteger abstractId = document.getNumbering().getNum(firstId)
                    .getCTNum().getAbstractNumId().getVal();
            assertThat(document.getNumbering().getAbstractNum(abstractId))
                    .isNotNull();
        }
    }

    @Test
    void reusesConfiguredCompatibleTemplateNumId() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            WordNumberingManager existing = new WordNumberingManager(document);
            int numCount = document.getNumbering().getNums().size();
            WordNumberingDefinition definition = new WordNumberingDefinition();
            definition.setNumId(Long.valueOf(existing.getNumId().longValue()));

            WordNumberingManager reused =
                    new WordNumberingManager(document, definition);

            assertThat(reused.getNumId()).isEqualTo(existing.getNumId());
            assertThat(document.getNumbering().getNums()).hasSize(numCount);
        }
    }

    @Test
    void createsConfiguredFourLevelFormatAndTextAndAppliesLevelIndex()
            throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            WordNumberingDefinition definition = new WordNumberingDefinition();
            definition.setLevels(Arrays.asList(
                    level(1, "decimal", "第%1章"),
                    level(2, "lowerLetter", "%1.%2"),
                    level(3, "lowerRoman", "（%3）"),
                    level(4, "decimalEnclosedCircle", "%4")));

            WordNumberingManager manager =
                    new WordNumberingManager(document, definition);
            XWPFParagraph paragraph = document.createParagraph();
            manager.apply(paragraph, 3);

            BigInteger abstractId = document.getNumbering()
                    .getNum(manager.getNumId()).getCTNum()
                    .getAbstractNumId().getVal();
            CTAbstractNum abstractNum = document.getNumbering()
                    .getAbstractNum(abstractId).getCTAbstractNum();
            CTLvl first = abstractNum.getLvlArray(0);
            CTLvl second = abstractNum.getLvlArray(1);

            assertThat(first.getNumFmt().getVal().toString())
                    .isEqualTo("decimal");
            assertThat(first.getLvlText().getVal()).isEqualTo("第%1章");
            assertThat(second.getNumFmt().getVal().toString())
                    .isEqualTo("lowerLetter");
            assertThat(second.getLvlText().getVal()).isEqualTo("%1.%2");
            assertThat(paragraph.getNumIlvl()).isEqualTo(BigInteger.valueOf(2));
        }
    }

    @Test
    void rejectsMissingOrIncompatibleExplicitTemplateNumId()
            throws Exception {
        try (XWPFDocument missing = new XWPFDocument()) {
            WordNumberingDefinition definition =
                    new WordNumberingDefinition();
            definition.setNumId(Long.valueOf(77L));
            assertThatThrownBy(() ->
                    new WordNumberingManager(missing, definition))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("77")
                    .hasMessageContaining("compatible");
        }

        try (XWPFDocument incompatible = new XWPFDocument()) {
            WordNumberingManager existing =
                    new WordNumberingManager(incompatible);
            WordNumberingDefinition definition =
                    new WordNumberingDefinition();
            definition.setNumId(Long.valueOf(
                    existing.getNumId().longValue()));
            definition.setLevels(Arrays.asList(
                    level(1, "upperRoman", "%1"),
                    level(2, "lowerLetter", "%1.%2"),
                    level(3, "lowerRoman", "%1.%2.%3"),
                    level(4, "decimal", "%1.%2.%3.%4")));
            assertThatThrownBy(() ->
                    new WordNumberingManager(incompatible, definition))
                    .isInstanceOf(WordTemplateException.class)
                    .hasMessageContaining("compatible");
        }
    }

    private static WordNumberingLevelDefinition level(
            int level, String format, String text) {
        WordNumberingLevelDefinition definition =
                new WordNumberingLevelDefinition();
        definition.setLevel(level);
        definition.setNumFmt(format);
        definition.setLvlText(text);
        return definition;
    }
}
