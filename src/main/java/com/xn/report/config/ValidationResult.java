package com.xn.report.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ValidationResult {

    private final List<ValidationIssue> issues = new ArrayList<ValidationIssue>();

    public boolean isValid() {
        return issues.isEmpty();
    }

    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(new ArrayList<ValidationIssue>(issues));
    }

    public List<String> codes() {
        List<String> codes = issues.stream()
                .map(ValidationIssue::getCode)
                .collect(Collectors.toList());
        return Collections.unmodifiableList(codes);
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            String summary = issues.stream()
                    .map(ValidationIssue::toString)
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Invalid report definition: " + summary);
        }
    }

    void add(String code, String path, String message) {
        issues.add(new ValidationIssue(code, path, message));
    }
}
