package com.xn.report.error;

public enum ReportErrorCode {

    SQL_004("SQL-004"),
    DATA_001("DATA-001"),
    DATA_002("DATA-002"),
    DATA_003("DATA-003"),
    DATA_004("DATA-004"),
    RULE_001("RULE-001"),
    RULE_002("RULE-002"),
    TEXT_001("TEXT-001"),
    CHART_001("CHART-001"),
    CHART_002("CHART-002"),
    CHART_003("CHART-003");

    private final String code;

    ReportErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
