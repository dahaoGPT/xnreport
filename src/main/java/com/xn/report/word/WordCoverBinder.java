package com.xn.report.word;

import com.xn.report.config.definition.WordCoverDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Word 文档封面占位符绑定器。
 * <p>
 * 替换模板中预置的五大固定封面占位符：
 * <ul>
 *   <li><code>{{cover:title}}</code> - 报表主标题。</li>
 *   <li><code>{{cover:organization}}</code> - 机构/组织名称。</li>
 *   <li><code>{{cover:reportPeriod}}</code> - 报表周期。</li>
 *   <li><code>{{cover:preparedBy}}</code> - 编制人。</li>
 *   <li><code>{{cover:preparedDate}}</code> - 编制日期。</li>
 * </ul>
 * 若模板缺失任意一个必填封面占位符，将主动抛出 {@link WordTemplateException}。
 * </p>
 */
public final class WordCoverBinder {

    public static final String REPORT_TITLE = "{{cover:title}}";
    public static final String ORGANIZATION = "{{cover:organization}}";
    public static final String REPORT_PERIOD = "{{cover:reportPeriod}}";
    public static final String PREPARED_BY = "{{cover:preparedBy}}";
    public static final String PREPARED_DATE = "{{cover:preparedDate}}";

    private final WordRunTextReplacer replacer;

    public WordCoverBinder(WordRunTextReplacer replacer) {
        if (replacer == null) {
            throw new IllegalArgumentException("Word text replacer is required");
        }
        this.replacer = replacer;
    }

    /**
     * 校验并绑定封面元数据到文档。
     *
     * @param document 目标 Word 文档
     * @param cover 封面配置定义
     */
    public void bind(XWPFDocument document, WordCoverDefinition cover) {
        if (document == null || cover == null) {
            throw new IllegalArgumentException(
                    "Word document and cover definition are required");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        values.put(REPORT_TITLE, required(cover.getTitle(), "reportTitle"));
        values.put(ORGANIZATION, required(cover.getOrganization(), "organization"));
        values.put(REPORT_PERIOD, required(cover.getReportPeriod(), "reportPeriod"));
        values.put(PREPARED_BY, required(cover.getPreparedBy(), "preparedBy"));
        values.put(PREPARED_DATE, required(cover.getPreparedDate(), "preparedDate"));

        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (replacer.count(document, entry.getKey()) == 0) {
                throw new WordTemplateException(
                        "Word template is missing required cover placeholder "
                                + entry.getKey());
            }
        }
        replacer.replaceAll(document, values);
    }

    private static String required(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Word cover value " + name + " is required");
        }
        return value;
    }
}
