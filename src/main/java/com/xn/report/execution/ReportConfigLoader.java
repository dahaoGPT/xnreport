package com.xn.report.execution;

import com.xn.report.config.ReportDefinition;
import java.nio.file.Path;

@FunctionalInterface
public interface ReportConfigLoader {

    ReportDefinition load(Path path);
}
