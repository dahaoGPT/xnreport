package com.xn.report.dataset;

import com.xn.report.config.definition.DatasetDefinition;

interface DatasetExecutionObserver {

    void afterExecution(
            DatasetDefinition definition, DatasetResult result);
}
