package com.xn.report.dataset;

import java.nio.file.Path;

@FunctionalInterface
public interface DatasetQueryServiceFactory {
    DatasetQueryService create(Path sqlRoot);
}
