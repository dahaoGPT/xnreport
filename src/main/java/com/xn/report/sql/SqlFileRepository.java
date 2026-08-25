package com.xn.report.sql;

import com.xn.report.config.RootPathPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 外部 SQL 脚本文件读取与沙箱仓储。
 * <p>
 * 基于 {@link RootPathPolicy} 在受信任的 SQL 根目录下安全解析并读取 UTF-8 编码的 SQL 文件文本，
 * 自动剥离 UTF-8 文件的 BOM（Byte Order Mark）头字符，防止解析异常。
 * </p>
 */
public final class SqlFileRepository {

    /** 根路径安全校验策略。 */
    private final RootPathPolicy rootPathPolicy;

    /**
     * 构造 SQL 文件仓储。
     *
     * @param rootPathPolicy 根路径安全策略，不可为 null
     */
    public SqlFileRepository(RootPathPolicy rootPathPolicy) {
        this.rootPathPolicy = Objects.requireNonNull(
                rootPathPolicy, "rootPathPolicy");
    }

    /**
     * 读取指定相对路径下的 SQL 文件内容。
     *
     * @param path 相对或绝对路径字符串
     * @return SQL 纯文本内容（已移除 BOM）
     * @throws IllegalArgumentException 如果文件不存在、不可读或逃逸出安全根目录
     */
    public String read(String path) {
        return readResolved(rootPathPolicy.resolve(path));
    }

    /**
     * 读取指定 Path 对象的 SQL 文件内容。
     *
     * @param path Path 对象
     * @return SQL 纯文本内容
     */
    public String read(Path path) {
        return readResolved(rootPathPolicy.resolve(path));
    }

    /**
     * 执行底层文件读取并净化 BOM 头。
     */
    private String readResolved(Path path) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Not a readable SQL file: " + path);
        }
        try {
            String sql = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return sql.startsWith("\uFEFF") ? sql.substring(1) : sql;
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException(
                    "Cannot read SQL file " + path, exception);
        }
    }
}
