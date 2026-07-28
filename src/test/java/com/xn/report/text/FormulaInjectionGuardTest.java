package com.xn.report.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FormulaInjectionGuardTest {

    private final FormulaInjectionGuard guard = new FormulaInjectionGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "=SUM(A1:A2)", "+1+1", "-2+3", "@cmd",
            " =SUM(A1:A2)", "\t@cmd", "\u0000+1", "\u200B-1"
    })
    void prefixesDangerousExcelTextIncludingObfuscatedPrefixes(String input) {
        assertThat(guard.asPlainText(input)).isEqualTo("'" + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "  text", "123", "'=alreadyText", ""})
    void leavesSafeTextUnchanged(String input) {
        assertThat(guard.asPlainText(input)).isEqualTo(input);
    }
}
