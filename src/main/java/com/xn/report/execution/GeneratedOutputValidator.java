package com.xn.report.execution;

import java.nio.file.Path;

@FunctionalInterface
public interface GeneratedOutputValidator {

    void validate(Path excel, Path word);
}
