package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public final class WordTemplateChartBinder {

    public void bind(
            XWPFDocument document,
            String chartId,
            RenderedChart chart,
            WordComponentDefinition component) {
        if (document == null || chartId == null
                || chartId.trim().isEmpty()
                || chart == null || component == null) {
            throw new IllegalArgumentException(
                    "Word document, chart id, rendered chart and component"
                            + " are required");
        }
        String marker = "{{chart:" + chartId + "}}";
        if (WordPackageTextScanner.count(document, marker) != 1) {
            throw new WordTemplateException(
                    "Word chart marker " + marker
                            + " must appear exactly once in the package");
        }
        XWPFParagraph target = null;
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                if (marker.equals(paragraph.getText().trim())) {
                    target = paragraph;
                    break;
                }
            }
        }
        if (target == null) {
            throw new WordTemplateException(
                    "Word chart marker " + marker
                            + " must be a standalone top-level body paragraph");
        }
        new WordImageWriter().write(
                document, target, chart, component);
    }
}
