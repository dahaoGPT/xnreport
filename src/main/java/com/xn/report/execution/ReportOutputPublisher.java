package com.xn.report.execution;

import com.xn.report.output.CollisionPolicy;
import com.xn.report.output.OutputTargets;
import com.xn.report.output.PublishedOutputs;
import java.nio.file.Path;

@FunctionalInterface
public interface ReportOutputPublisher {

    PublishedOutputs publish(
            Path sourceExcel,
            Path sourceWord,
            OutputTargets targets,
            Path outputRoot,
            CollisionPolicy collisionPolicy);
}
