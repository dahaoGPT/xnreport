package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import java.util.Collections;
import java.util.List;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

public final class WordTemplateChartBinder {

    public void bind(
            XWPFDocument document,
            String chartId,
            RenderedChart chart,
            WordComponentDefinition component) {
        bindAll(document, chartId, Collections.singletonList(chart), component);
    }

    public void bindAll(
            XWPFDocument document,
            String chartId,
            List<RenderedChart> charts,
            WordComponentDefinition component) {
        if (document == null || chartId == null
                || chartId.trim().isEmpty()
                || charts == null || charts.isEmpty()
                || charts.contains(null) || component == null) {
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
        java.util.ArrayList<XWPFParagraph> targets =
                new java.util.ArrayList<XWPFParagraph>();
        targets.add(target);
        XWPFParagraph previous = target;
        for (int index = 1; index < charts.size(); index++) {
            previous = insertAfter(document, previous);
            targets.add(previous);
        }
        WordImageWriter writer = new WordImageWriter();
        for (int index = 0; index < charts.size(); index++) {
            writer.write(document, targets.get(index), charts.get(index),
                    component);
        }
    }

    private static XWPFParagraph insertAfter(
            XWPFDocument document, XWPFParagraph paragraph) {
        List<IBodyElement> body = document.getBodyElements();
        int index = body.indexOf(paragraph);
        if (index < 0 || index + 1 >= body.size()) {
            return document.createParagraph();
        }
        IBodyElement next = body.get(index + 1);
        XmlObject xml = next instanceof XWPFParagraph
                ? ((XWPFParagraph) next).getCTP()
                : ((XWPFTable) next).getCTTbl();
        XmlCursor cursor = xml.newCursor();
        try {
            return document.insertNewParagraph(cursor);
        } finally {
            cursor.dispose();
        }
    }
}
