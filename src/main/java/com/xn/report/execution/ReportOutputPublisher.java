package com.xn.report.execution;

import com.xn.report.output.CollisionPolicy;
import com.xn.report.output.OutputTargets;
import com.xn.report.output.PublishedOutputs;
import java.nio.file.Path;

/**
 * 报表最终产物发布与归档函数式接口。
 */
@FunctionalInterface
public interface ReportOutputPublisher {

    /**
     * 将临时产物发布到目标路径。
     *
     * @param sourceExcel 临时生成的 Excel 路径
     * @param sourceWord 临时生成的 Word 路径
     * @param targets 目标输出路径对
     * @param outputRoot 输出根目录
     * @param collisionPolicy 同名文件冲突策略
     * @return 已发布的产物信息
     */
    PublishedOutputs publish(
            Path sourceExcel,
            Path sourceWord,
            OutputTargets targets,
            Path outputRoot,
            CollisionPolicy collisionPolicy);
}
