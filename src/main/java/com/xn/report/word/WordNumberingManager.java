package com.xn.report.word;

import com.xn.report.config.definition.WordNumberingDefinition;
import com.xn.report.config.definition.WordNumberingLevelDefinition;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
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
        this(document, new WordNumberingDefinition());
    }

    public WordNumberingManager(
            XWPFDocument document, WordNumberingDefinition definition) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
        WordNumberingDefinition effective = definition == null
                ? new WordNumberingDefinition() : definition;
        Map<Integer, WordNumberingLevelDefinition> levels =
                normalizedLevels(effective.getLevels());
        XWPFNumbering numbering = document.getNumbering();
        if (numbering == null) {
            numbering = document.createNumbering();
        }
        BigInteger reusable = reusableNumId(numbering, effective, levels);
        if (effective.getNumId() != null && reusable == null) {
            throw new WordTemplateException(
                    "Configured Word numId " + effective.getNumId()
                            + " does not exist or is not compatible with"
                            + " the configured four-level numbering");
        }
        this.numId = reusable == null
                ? createNumbering(numbering, levels) : reusable;
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

    private static BigInteger reusableNumId(
            XWPFNumbering numbering,
            WordNumberingDefinition definition,
            Map<Integer, WordNumberingLevelDefinition> levels) {
        if (definition.getNumId() != null) {
            BigInteger configured =
                    BigInteger.valueOf(definition.getNumId().longValue());
            return compatible(numbering, configured, levels)
                    ? configured : null;
        }
        for (XWPFNum candidate : numbering.getNums()) {
            BigInteger candidateId = candidate.getCTNum().getNumId();
            if (compatible(numbering, candidateId, levels)) {
                return candidateId;
            }
        }
        return null;
    }

    private static boolean compatible(
            XWPFNumbering numbering,
            BigInteger candidateId,
            Map<Integer, WordNumberingLevelDefinition> levels) {
        if (candidateId == null || !numbering.numExist(candidateId)) {
            return false;
        }
        XWPFNum num = numbering.getNum(candidateId);
        if (num == null || num.getCTNum().getAbstractNumId() == null) {
            return false;
        }
        XWPFAbstractNum wrapper = numbering.getAbstractNum(
                num.getCTNum().getAbstractNumId().getVal());
        if (wrapper == null || wrapper.getCTAbstractNum() == null) {
            return false;
        }
        CTAbstractNum abstractNum = wrapper.getCTAbstractNum();
        for (Map.Entry<Integer, WordNumberingLevelDefinition> entry
                : levels.entrySet()) {
            CTLvl actual = findLevel(abstractNum, entry.getKey().intValue() - 1);
            WordNumberingLevelDefinition expected = entry.getValue();
            if (actual == null || !actual.isSetNumFmt()
                    || !actual.isSetLvlText()
                    || !expected.getNumFmt().equals(
                    actual.getNumFmt().getVal().toString())
                    || !expected.getLvlText().equals(
                    actual.getLvlText().getVal())) {
                return false;
            }
        }
        return true;
    }

    private static CTLvl findLevel(CTAbstractNum abstractNum, int ilvl) {
        for (CTLvl level : abstractNum.getLvlArray()) {
            if (level.getIlvl() != null
                    && level.getIlvl().intValue() == ilvl) {
                return level;
            }
        }
        return null;
    }

    private static BigInteger createNumbering(
            XWPFNumbering numbering,
            Map<Integer, WordNumberingLevelDefinition> levels) {
        CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(nextAbstractNumId(numbering));
        abstractNum.addNewMultiLevelType().setVal(STMultiLevelType.MULTILEVEL);
        for (Map.Entry<Integer, WordNumberingLevelDefinition> entry
                : levels.entrySet()) {
            int level = entry.getKey().intValue();
            WordNumberingLevelDefinition configured = entry.getValue();
            addLevel(abstractNum, level - 1,
                    format(configured.getNumFmt()),
                    configured.getLvlText(),
                    (level - 1) * 360, level * 360);
        }
        BigInteger abstractId = numbering.addAbstractNum(
                new XWPFAbstractNum(abstractNum));
        return numbering.addNum(abstractId);
    }

    private static BigInteger nextAbstractNumId(XWPFNumbering numbering) {
        BigInteger next = BigInteger.ZERO;
        for (XWPFAbstractNum existing : numbering.getAbstractNums()) {
            if (existing.getCTAbstractNum() != null
                    && existing.getCTAbstractNum().getAbstractNumId() != null) {
                BigInteger candidate = existing.getCTAbstractNum()
                        .getAbstractNumId().add(BigInteger.ONE);
                if (candidate.compareTo(next) > 0) {
                    next = candidate;
                }
            }
        }
        return next;
    }

    private static Map<Integer, WordNumberingLevelDefinition> normalizedLevels(
            List<WordNumberingLevelDefinition> configured) {
        Map<Integer, WordNumberingLevelDefinition> levels =
                new LinkedHashMap<Integer, WordNumberingLevelDefinition>();
        if (configured != null) {
            for (WordNumberingLevelDefinition level : configured) {
                if (level != null) {
                    levels.put(Integer.valueOf(level.getLevel()), level);
                }
            }
        }
        if (levels.size() != 4
                || !levels.containsKey(Integer.valueOf(1))
                || !levels.containsKey(Integer.valueOf(2))
                || !levels.containsKey(Integer.valueOf(3))
                || !levels.containsKey(Integer.valueOf(4))) {
            throw new IllegalArgumentException(
                    "Word numbering requires exactly levels 1 through 4");
        }
        for (WordNumberingLevelDefinition level : levels.values()) {
            if (format(level.getNumFmt()) == null
                    || level.getLvlText() == null
                    || level.getLvlText().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Word numbering format and level text are required");
            }
        }
        return levels;
    }

    private static STNumberFormat.Enum format(String value) {
        return value == null ? null : STNumberFormat.Enum.forString(value);
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
