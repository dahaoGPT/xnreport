package com.xn.report.execution;

import com.xn.report.config.ReportDefinition;
import java.nio.file.Path;

/**
 * 报表配置文件加载函数式接口。
 */
@FunctionalInterface
public interface ReportConfigLoader {

    /**
     * 从指定 JSON 路径加载报表配置定义。
     *
     * @param path 配置文件绝对路径
     * @return 报表定义对象
     */
    ReportDefinition load(Path path);
}
