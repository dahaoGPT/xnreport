package com.xn.report.word;

import java.math.BigInteger;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMultiLevelType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

public final class WordNumberingManager {

    private final BigInteger numId;

    public WordNumberingManager(XWPFDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
        XWPFNumbering numbering = document.getNumbering();
        if (numbering == null) {
            numbering = document.createNumbering();
        }
        CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(BigInteger.ZERO);
        abstractNum.addNewMultiLevelType().setVal(STMultiLevelType.MULTILEVEL);
        addLevel(abstractNum, 0, STNumberFormat.CHINESE_COUNTING, "%1、", 0, 360);
        addLevel(abstractNum, 1, STNumberFormat.DECIMAL, "%1.%2", 360, 720);
        addLevel(abstractNum, 2, STNumberFormat.DECIMAL, "（%3）", 720, 1080);
        addLevel(abstractNum, 3, STNumberFormat.DECIMAL_ENCLOSED_CIRCLE,
                "%4", 1080, 1440);
        BigInteger abstractId = numbering.addAbstractNum(
                new XWPFAbstractNum(abstractNum));
        this.numId = numbering.addNum(abstractId);
    }

    public BigInteger apply(XWPFParagraph paragraph, int level) {
        if (paragraph == null) {
            throw new IllegalArgumentException("Word paragraph is required");
        }
        if (level < 1 || level > 4) {
            throw new IllegalArgumentException(
                    "Heading numbering level must be between 1 and 4");
        }
        paragraph.setNumID(numId);
        paragraph.setNumILvl(BigInteger.valueOf(level - 1L));
        return numId;
    }

    public BigInteger getNumId() {
        return numId;
    }

    private static void addLevel(
            CTAbstractNum abstractNum,
            int level,
            STNumberFormat.Enum format,
            String text,
            int left,
            int hanging) {
        CTLvl item = abstractNum.addNewLvl();
        item.setIlvl(BigInteger.valueOf(level));
        item.addNewStart().setVal(BigInteger.ONE);
        item.addNewNumFmt().setVal(format);
        item.addNewLvlText().setVal(text);
        item.addNewLvlJc().setVal(STJc.LEFT);
        item.addNewPPr().addNewInd()
                .setLeft(BigInteger.valueOf(left + hanging));
        item.getPPr().getInd().setHanging(BigInteger.valueOf(hanging));
    }
}
