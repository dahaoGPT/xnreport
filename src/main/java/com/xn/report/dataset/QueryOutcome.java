package com.xn.report.dataset;

import com.xn.report.policy.ReportWarning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class QueryOutcome {
    private final DatasetContext datasets;
    private final List<ReportWarning> warnings;

    public QueryOutcome(DatasetContext datasets, List<ReportWarning> warnings) {
        this.datasets = Objects.requireNonNull(datasets, "datasets");
        this.warnings = Collections.unmodifiableList(
                new ArrayList<ReportWarning>(warnings == null
                        ? Collections.<ReportWarning>emptyList() : warnings));
    }

    public DatasetContext getDatasets() { return datasets; }
    public List<ReportWarning> getWarnings() { return warnings; }
}
