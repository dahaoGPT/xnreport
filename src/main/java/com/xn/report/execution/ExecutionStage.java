package com.xn.report.execution;

public enum ExecutionStage {
    INITIALIZE,
    LOAD_CONFIG,
    VALIDATE_CONFIG,
    QUERY,
    ANALYZE,
    GENERATE_EXCEL,
    GENERATE_WORD,
    VALIDATE_OUTPUTS,
    PUBLISH,
    COMPLETED
}
