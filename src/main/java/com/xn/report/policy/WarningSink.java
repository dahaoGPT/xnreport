package com.xn.report.policy;

@FunctionalInterface
public interface WarningSink {

    void accept(ReportWarning warning);

    static WarningSink ignoring() {
        return warning -> {
        };
    }
}
