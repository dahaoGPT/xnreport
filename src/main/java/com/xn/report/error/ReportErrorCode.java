package com.xn.report.error;

public enum ReportErrorCode {

    CFG_001("CFG-001"),
    CFG_002("CFG-002"),
    CFG_003("CFG-003"),
    CFG_004("CFG-004"),
    SQL_001("SQL-001"),
    SQL_002("SQL-002"),
    SQL_003("SQL-003"),
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
    CHART_003("CHART-003"),
    XLSX_001("XLSX-001"),
    XLSX_002("XLSX-002"),
    DOCX_001("DOCX-001"),
    DOCX_002("DOCX-002"),
    DOCX_003("DOCX-003"),
    OUT_001("OUT-001"),
    OUT_002("OUT-002"),
    OUT_003("OUT-003");

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
