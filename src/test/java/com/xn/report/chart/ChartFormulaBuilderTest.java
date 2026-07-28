package com.xn.report.chart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChartFormulaBuilderTest {

    private final ChartFormulaBuilder builder = new ChartFormulaBuilder();

    @Test
    void quotesSheetNamesAndEscapesApostrophes() {
        assertThat(builder.range("中心-'每月", 0, 1, 6))
                .isEqualTo("'中心-''每月'!$A$2:$A$7");
        assertThat(builder.cell("中心-'每月", 2, 0))
                .isEqualTo("'中心-''每月'!$C$1");
    }

    @Test
    void emitsLegalSingleBlankCellReferenceForZeroPoints() {
        assertThat(builder.range("中心-每月", 0, 1, 0))
                .isEqualTo("'中心-每月'!$A$2:$A$2");
    }
}
