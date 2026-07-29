package com.xn.report.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.error.ReportException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputNameRendererTest {

    private final OutputNameRenderer renderer = new OutputNameRenderer(80);

    @Test
    void rendersVariablesSanitizesWindowsCharactersAndKeepsExtension() {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("period", "2026:07");

        assertThat(renderer.render("效能报告_${period}?.xlsx", values))
                .isEqualTo("效能报告_2026_07_.xlsx");
        assertThat(renderer.render("report. .docx", values))
                .isEqualTo("report.docx");
    }

    @Test
    void rejectsAbsoluteTraversalAndDirectorySeparatorsBeforeSanitizing() {
        assertThatThrownBy(() -> renderer.render("../outside.xlsx", Collections.emptyMap()))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("path");
        assertThatThrownBy(() -> renderer.render("folder/report.xlsx", Collections.emptyMap()))
                .isInstanceOf(ReportException.class);
        assertThatThrownBy(() -> renderer.render("C:\\outside.xlsx", Collections.emptyMap()))
                .isInstanceOf(ReportException.class);
    }

    @Test
    void truncatesBaseNameWithoutLosingOrChangingRequiredExtension() {
        String rendered = renderer.render(
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz.xlsx",
                Collections.emptyMap());

        assertThat(rendered).hasSize(80).endsWith(".xlsx");
        assertThatThrownBy(() -> renderer.render("report.pdf", Collections.emptyMap()))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void rejectsNameThatBecomesEmptyWhenTruncationLeavesOnlyTrailingDots() {
        OutputNameRenderer shortRenderer = new OutputNameRenderer(10);

        assertThatThrownBy(() -> shortRenderer.render(
                "     x.xlsx", Collections.emptyMap()))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsPlaceholderFragmentsIntroducedByVariableValues() {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("period", "${injected}");

        assertThatThrownBy(() -> renderer.render("report_${period}.xlsx", values))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("placeholder");
        values.put("period", "${");
        assertThatThrownBy(() -> renderer.render("report_${period}.xlsx", values))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void rewritesWindowsReservedDeviceNames() {
        assertThat(renderer.render("CON.xlsx", Collections.emptyMap()))
                .isEqualTo("_CON.xlsx");
        assertThat(renderer.render("lpt9.docx", Collections.emptyMap()))
                .isEqualTo("_lpt9.docx");
        assertThat(renderer.render("COM1.report.xlsx", Collections.emptyMap()))
                .isEqualTo("_COM1.report.xlsx");
    }
}
