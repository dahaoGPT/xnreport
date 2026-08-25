package com.xn.report.output;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 报表单次执行隔离的临时工作空间。
 * <p>
 * 为每一次报表生成任务在 {@code tempRoot} 下创建唯一的子目录（以 executionId 命名），
 * 并在其中分别创建 {@code excel/}、{@code word/}、{@code charts/} 隔离子目录。
 * 实现了安全路径归属检查（防止符号链接逃逸与目录穿越安全漏洞），
 * 并在任务结束（{@link #close()}）时自动递归清理所有临时生成的文件与目录。
 * </p>
 */
public final class ExecutionWorkspace implements AutoCloseable {

    /** 执行唯一标识（UUID）。 */
    private final String executionId;

    /** 配置的临时根目录绝对规范化路径。 */
    private final Path tempRoot;

    /** 当前执行的工作空间根目录路径。 */
    private final Path root;

    /** 当前执行工作空间真实物理路径（已解析符号链接）。 */
    private final Path realRoot;

    /** Excel 生成中间文件临时存放目录。 */
    private final Path excelDirectory;

    /** Word 生成中间文件临时存放目录。 */
    private final Path wordDirectory;

    /** JFreeChart 图表渲染图片临时存放目录。 */
    private final Path chartsDirectory;

    /** 删除策略（便于单测 Mock 异常场景）。 */
    private final DeleteStrategy deleteStrategy;

    /** 是否已关闭/已清理。 */
    private boolean closed;

    /** 私有构造函数。 */
    private ExecutionWorkspace(
            String executionId,
            Path tempRoot,
            Path root,
            Path realRoot,
            Path excelDirectory,
            Path wordDirectory,
            Path chartsDirectory,
            DeleteStrategy deleteStrategy) {
        this.executionId = executionId;
        this.tempRoot = tempRoot;
        this.root = root;
        this.realRoot = realRoot;
        this.excelDirectory = excelDirectory;
        this.wordDirectory = wordDirectory;
        this.chartsDirectory = chartsDirectory;
        this.deleteStrategy = deleteStrategy;
    }

    /**
     * 在指定的临时根目录下创建默认工作空间。
     *
     * @param configuredTempRoot 配置的临时根目录路径
     * @return 初始化完成的临时工作空间实例
     */
    public static ExecutionWorkspace create(Path configuredTempRoot) {
        return create(
                configuredTempRoot,
                UUID.randomUUID().toString(),
                Files::createDirectory,
                Files::deleteIfExists);
    }

    /**
     * 内部工厂方法，支持自定义执行 ID 与文件操作策略（用于测试）。
     *
     * @param configuredTempRoot 配置的临时根目录
     * @param executionId 执行 ID
     * @param directoryCreator 目录创建函数
     * @param deleteStrategy 文件删除策略
     * @return 工作空间实例
     */
    static ExecutionWorkspace create(
            Path configuredTempRoot,
            String executionId,
            DirectoryCreator directoryCreator,
            DeleteStrategy deleteStrategy) {
        Objects.requireNonNull(configuredTempRoot, "configuredTempRoot");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(directoryCreator, "directoryCreator");
        Objects.requireNonNull(deleteStrategy, "deleteStrategy");

        Path tempRoot = configuredTempRoot.toAbsolutePath().normalize();
        Path root = tempRoot.resolve(executionId).normalize();

        // 安全检查：防止空 ID 或路径逃逸到 tempRoot 之外
        if (executionId.trim().isEmpty()
                || !root.getParent().equals(tempRoot)) {
            throw invalidPath("workspace path is outside temp root");
        }

        boolean rootCreated = false;
        try {
            Files.createDirectories(tempRoot);
            Path realTempRoot = tempRoot.toRealPath();
            directoryCreator.create(root);
            rootCreated = true;
            Path realRoot = root.toRealPath();

            // 物理路径逃逸校验（防软链接穿越）
            if (!realRoot.getParent().equals(realTempRoot)) {
                throw new IOException("workspace real path escaped temp root");
            }

            // 创建专用的子功能目录
            Path excel = directoryCreator.create(root.resolve("excel"));
            Path word = directoryCreator.create(root.resolve("word"));
            Path charts = directoryCreator.create(root.resolve("charts"));

            return new ExecutionWorkspace(
                    executionId,
                    tempRoot,
                    root,
                    realRoot,
                    excel,
                    word,
                    charts,
                    deleteStrategy);
        } catch (IOException ex) {
            // 如果创建子目录失败，安全清理已创建的根目录
            if (rootCreated) {
                IOException cleanupFailure =
                        cleanupTree(root, deleteStrategy);
                if (cleanupFailure != null) {
                    ex.addSuppressed(cleanupFailure);
                }
            }
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "cannot create execution workspace under temp root",
                    ex);
        }
    }

    /**
     * 解析相对于工作空间根目录的相对路径，并校验其安全归属性。
     *
     * @param relativePath 相对路径
     * @return 规范化后的绝对路径
     */
    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path resolved = root.resolve(relativePath).normalize();
        assertOwned(resolved);
        return resolved;
    }

    /**
     * 断言给定路径属于当前工作空间，防止越权访问或路径穿越。
     *
     * @param path 待校验路径
     * @throws ReportException 如果路径超出工作空间范围
     */
    public void assertOwned(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw invalidPath("path is outside execution workspace");
        }
        Path existing = normalized;
        while (existing != null
                && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw invalidPath("workspace path has no existing owned ancestor");
        }
        try {
            Path realExisting = existing.toRealPath();
            if (!realExisting.startsWith(realRoot)) {
                throw invalidPath("path resolves outside execution workspace");
            }
        } catch (IOException ex) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "cannot verify workspace path ownership",
                    ex);
        }
    }

    public String getExecutionId() {
        return executionId;
    }

    public Path getRoot() {
        return root;
    }

    public Path getExcelDirectory() {
        return excelDirectory;
    }

    public Path getWordDirectory() {
        return wordDirectory;
    }

    public Path getChartsDirectory() {
        return chartsDirectory;
    }

    /**
     * 关闭并清理当前工作空间，递归删除所有临时文件。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!root.getParent().equals(tempRoot) || !root.startsWith(tempRoot)) {
            throw invalidPath("refusing to clean a workspace outside temp root");
        }
        if (!Files.exists(root)) {
            closed = true;
            return;
        }
        IOException failure = cleanupTree(root, deleteStrategy);
        if (failure != null) {
            throw new ReportException(
                    ReportErrorCode.OUT_003,
                    "cannot clean execution workspace; cleanup can be retried",
                    failure);
        }
        closed = true;
    }

    private static ReportException invalidPath(String message) {
        return new ReportException(ReportErrorCode.OUT_001, message);
    }

    /**
     * 从最深层子节点开始逆序递归删除整个目录树。
     */
    private static IOException cleanupTree(
            Path root, DeleteStrategy deleteStrategy) {
        List<Path> all = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(all::add);
        } catch (IOException ex) {
            return ex;
        }
        IOException failure = null;
        for (Path path : all) {
            try {
                deleteStrategy.delete(path);
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        return failure;
    }

    /** 目录创建接口。 */
    @FunctionalInterface
    interface DirectoryCreator {
        Path create(Path path) throws IOException;
    }

    /** 文件删除接口。 */
    @FunctionalInterface
    interface DeleteStrategy {
        boolean delete(Path path) throws IOException;
    }
}
