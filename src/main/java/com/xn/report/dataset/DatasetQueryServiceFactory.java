package com.xn.report.dataset;

import java.nio.file.Path;

/**
 * 数据集查询服务实例创建工厂函数式接口。
 * <p>
 * 根据指定的 SQL 根目录路径（sqlRoot），构建具备安全沙箱隔离与只读事务保障的 {@link DatasetQueryService} 实例。
 * </p>
 */
@FunctionalInterface
public interface DatasetQueryServiceFactory {

    /**
     * 根据 SQL 文件根目录创建 DatasetQueryService 实例。
     *
     * @param sqlRoot SQL 脚本根目录绝对路径
     * @return DatasetQueryService 执行服务实例
     */
    DatasetQueryService create(Path sqlRoot);
}
