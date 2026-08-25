package com.xn.report.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * Word 跨 Run 碎片文本占位符精准查找与替换器。
 * <p>
 * 解决 Word 在排版编辑过程中常将一个连续占位符（如 <code>{{cover:title}}</code>）破碎分割到多个相邻 {@link XWPFRun} 中的经典难题：
 * <ul>
 *   <li>拼接段落完整文本并定位所有目标 token 的起止下标。</li>
 *   <li>精确定位匹配跨度的首个 Run 与末尾 Run，仅替换匹配区间，最大化保留两端 Run 原有的字体、字号、颜色与高亮样式。</li>
 *   <li>支持整篇文档（含正文、页眉 Header、页脚 Footer、表格单元格）的批量安全替换与词频统计。</li>
 * </ul>
 * </p>
 */
public final class WordRunTextReplacer {

    /**
     * 批量替换整篇文档中的所有占位符。
     *
     * @param document 目标 Word 文档
     * @param replacements 占位符 -> 目标文本映射表
     * @return 成功替换的匹配项总数
     */
    public int replaceAll(XWPFDocument document, Map<String, String> replacements) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
        if (replacements == null || replacements.isEmpty()) {
            return 0;
        }
        List<Map.Entry<String, String>> entries =
                new ArrayList<Map.Entry<String, String>>(replacements.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(
                    Map.Entry<String, String> left,
                    Map.Entry<String, String> right) {
                return Integer.compare(right.getKey().length(), left.getKey().length());
            }
        });
        int replaced = replaceBody(document, entries);
        for (XWPFHeader header : headers(document)) {
            replaced += replaceBody(header, entries);
        }
        for (XWPFFooter footer : footers(document)) {
            replaced += replaceBody(footer, entries);
        }
        return replaced;
    }

    /**
     * 替换单个占位符。
     *
     * @param document 目标 Word 文档
     * @param token 待匹配占位符
     * @param replacement 替换目标文本
     * @return 成功替换的匹配项数量
     */
    public int replace(XWPFDocument document, String token, String replacement) {
        requireToken(token);
        return replaceAll(document, Collections.singletonMap(
                token, replacement == null ? "" : replacement));
    }

    /**
     * 在指定的容器体（正文或单元格）中执行批量替换。
     *
     * @param body 目标容器体
     * @param replacements 替换映射表
     * @return 替换计数
     */
    public int replaceInBody(IBody body, Map<String, String> replacements) {
        if (body == null) {
            throw new IllegalArgumentException("Word body is required");
        }
        if (replacements == null || replacements.isEmpty()) {
            return 0;
        }
        List<Map.Entry<String, String>> entries =
                new ArrayList<Map.Entry<String, String>>(replacements.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(
                    Map.Entry<String, String> left,
                    Map.Entry<String, String> right) {
                return Integer.compare(right.getKey().length(), left.getKey().length());
            }
        });
        return replaceBody(body, entries);
    }

    /**
     * 统计指定占位符在文档（含页眉页脚）中的出现总频次。
     *
     * @param document 目标文档
     * @param token 待匹配 token
     * @return 出现次数
     */
    public int count(XWPFDocument document, String token) {
        if (document == null) {
            throw new IllegalArgumentException("Word document is required");
        }
        requireToken(token);
        int count = countBody(document, token);
        for (XWPFHeader header : headers(document)) {
            count += countBody(header, token);
        }
        for (XWPFFooter footer : footers(document)) {
            count += countBody(footer, token);
        }
        return count;
    }

    private int replaceBody(
            IBody body, List<Map.Entry<String, String>> replacements) {
        int count = 0;
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                count += replaceParagraph(
                        (XWPFParagraph) element, replacements);
            } else if (element instanceof XWPFTable) {
                count += replaceTable((XWPFTable) element, replacements);
            }
        }
        return count;
    }

    private int replaceTable(
            XWPFTable table, List<Map.Entry<String, String>> replacements) {
        int count = 0;
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                count += replaceBody(cell, replacements);
            }
        }
        return count;
    }

    private int replaceParagraph(
            XWPFParagraph paragraph,
            List<Map.Entry<String, String>> replacements) {
        int count = 0;
        for (Map.Entry<String, String> replacement : replacements) {
            count += replaceToken(
                    paragraph,
                    replacement.getKey(),
                    replacement.getValue() == null ? "" : replacement.getValue());
        }
        return count;
    }

    private int replaceToken(
            XWPFParagraph paragraph, String token, String replacement) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return 0;
        }
        StringBuilder combined = new StringBuilder();
        int[] starts = new int[runs.size()];
        int[] ends = new int[runs.size()];
        for (int index = 0; index < runs.size(); index++) {
            starts[index] = combined.length();
            String text = safeText(runs.get(index));
            combined.append(text);
            ends[index] = combined.length();
        }
        List<Integer> matches = new ArrayList<Integer>();
        int from = 0;
        while (from <= combined.length() - token.length()) {
            int match = combined.indexOf(token, from);
            if (match < 0) {
                break;
            }
            matches.add(Integer.valueOf(match));
            from = match + token.length();
        }
        for (int matchIndex = matches.size() - 1; matchIndex >= 0; matchIndex--) {
            int start = matches.get(matchIndex).intValue();
            int end = start + token.length();
            int firstRun = runAt(starts, ends, start);
            int lastRun = runAt(starts, ends, end - 1);
            String firstText = safeText(runs.get(firstRun));
            String prefix = firstText.substring(0, start - starts[firstRun]);
            String suffix;
            if (firstRun == lastRun) {
                suffix = firstText.substring(end - starts[firstRun]);
            } else {
                String lastText = safeText(runs.get(lastRun));
                suffix = lastText.substring(end - starts[lastRun]);
            }
            setRunText(runs.get(firstRun), prefix + replacement
                    + (firstRun == lastRun ? suffix : ""));
            for (int index = firstRun + 1; index < lastRun; index++) {
                setRunText(runs.get(index), "");
            }
            if (lastRun > firstRun) {
                setRunText(runs.get(lastRun), suffix);
            }
        }
        return matches.size();
    }

    private int countBody(IBody body, String token) {
        int count = 0;
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                count += occurrences(((XWPFParagraph) element).getText(), token);
            } else if (element instanceof XWPFTable) {
                for (XWPFTableRow row : ((XWPFTable) element).getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        count += countBody(cell, token);
                    }
                }
            }
        }
        return count;
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while (text != null && from <= text.length() - token.length()) {
            int match = text.indexOf(token, from);
            if (match < 0) {
                break;
            }
            count++;
            from = match + token.length();
        }
        return count;
    }

    private static int runAt(int[] starts, int[] ends, int position) {
        for (int index = 0; index < starts.length; index++) {
            if (position >= starts[index] && position < ends[index]) {
                return index;
            }
        }
        throw new IllegalStateException("Unable to locate Word run for text position");
    }

    private static String safeText(XWPFRun run) {
        String text = run.text();
        return text == null ? "" : text;
    }

    private static void setRunText(XWPFRun run, String text) {
        if (run.getCTR().sizeOfTArray() == 0) {
            run.setText(text);
        } else {
            run.setText(text, 0);
            while (run.getCTR().sizeOfTArray() > 1) {
                run.getCTR().removeT(1);
            }
        }
    }

    private static void requireToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Replacement token must not be empty");
        }
    }

    private static Set<XWPFHeader> headers(XWPFDocument document) {
        Set<XWPFHeader> headers = Collections.newSetFromMap(
                new IdentityHashMap<XWPFHeader, Boolean>());
        headers.addAll(document.getHeaderList());
        XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
        if (policy != null) {
            add(headers, policy.getDefaultHeader());
            add(headers, policy.getFirstPageHeader());
            add(headers, policy.getEvenPageHeader());
        }
        return headers;
    }

    private static Set<XWPFFooter> footers(XWPFDocument document) {
        Set<XWPFFooter> footers = Collections.newSetFromMap(
                new IdentityHashMap<XWPFFooter, Boolean>());
        footers.addAll(document.getFooterList());
        XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
        if (policy != null) {
            add(footers, policy.getDefaultFooter());
            add(footers, policy.getFirstPageFooter());
            add(footers, policy.getEvenPageFooter());
        }
        return footers;
    }

    private static <T> void add(Set<T> values, T value) {
        if (value != null) {
            values.add(value);
        }
    }
}
