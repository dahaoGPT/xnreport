package com.xn.report.error;

public enum ReportErrorCode {

    SQL_004("SQL-004"),
    DATA_001("DATA-001"),
    DATA_002("DATA-002"),
    DATA_003("DATA-003"),
    DATA_004("DATA-004");

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
