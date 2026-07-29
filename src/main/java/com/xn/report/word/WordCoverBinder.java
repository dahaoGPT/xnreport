package com.xn.report.word;

import com.xn.report.config.definition.WordCoverDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

public final class WordCoverBinder {

    public static final String REPORT_TITLE = "{{reportTitle}}";
    public static final String ORGANIZATION = "{{organization}}";
    public static final String REPORT_PERIOD = "{{reportPeriod}}";
    public static final String PREPARED_BY = "{{preparedBy}}";
    public static final String PREPARED_DATE = "{{preparedDate}}";

    private final WordRunTextReplacer replacer;

    public WordCoverBinder(WordRunTextReplacer replacer) {
        if (replacer == null) {
            throw new IllegalArgumentException("Word text replacer is required");
        }
        this.replacer = replacer;
    }

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
