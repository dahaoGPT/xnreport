package com.xn.report.transform;

import com.xn.report.dataset.DatasetResult;

public interface Transform {

    DatasetResult apply(DatasetResult input);
}
