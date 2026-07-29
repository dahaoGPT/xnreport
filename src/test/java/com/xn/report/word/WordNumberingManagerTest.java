package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
}
