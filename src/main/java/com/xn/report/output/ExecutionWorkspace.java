package com.xn.report.output;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class ExecutionWorkspace implements AutoCloseable {

    private final String executionId;
    private final Path tempRoot;
    private final Path root;
    private final Path excelDirectory;
    private final Path wordDirectory;
    private final Path chartsDirectory;
    private boolean closed;

    private ExecutionWorkspace(
            String executionId,
            Path tempRoot,
            Path root,
            Path excelDirectory,
            Path wordDirectory,
            Path chartsDirectory) {
        this.executionId = executionId;
        this.tempRoot = tempRoot;
        this.root = root;
        this.excelDirectory = excelDirectory;
        this.wordDirectory = wordDirectory;
        this.chartsDirectory = chartsDirectory;
    }

    public static ExecutionWorkspace create(Path configuredTempRoot) {
        Objects.requireNonNull(configuredTempRoot, "configuredTempRoot");
        Path tempRoot = configuredTempRoot.toAbsolutePath().normalize();
        String executionId = UUID.randomUUID().toString();
        Path root = tempRoot.resolve(executionId).normalize();
        if (!root.getParent().equals(tempRoot)) {
            throw invalidPath("workspace path is outside temp root");
        }
        try {
            Files.createDirectories(tempRoot);
            Files.createDirectory(root);
            Path excel = Files.createDirectory(root.resolve("excel"));
            Path word = Files.createDirectory(root.resolve("word"));
            Path charts = Files.createDirectory(root.resolve("charts"));
            return new ExecutionWorkspace(
                    executionId, tempRoot, root, excel, word, charts);
        } catch (IOException ex) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "cannot create execution workspace under temp root",
                    ex);
        }
    }

    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path resolved = root.resolve(relativePath).normalize();
        assertOwned(resolved);
        return resolved;
    }

    public void assertOwned(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw invalidPath("path is outside execution workspace");
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

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!root.getParent().equals(tempRoot) || !root.startsWith(tempRoot)) {
            throw invalidPath("refusing to clean a workspace outside temp root");
        }
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new WorkspaceCleanupException(ex);
                }
            });
        } catch (WorkspaceCleanupException ex) {
            throw new ReportException(
                    ReportErrorCode.OUT_003,
                    "cannot clean execution workspace",
                    ex.getCause());
        } catch (IOException ex) {
            throw new ReportException(
                    ReportErrorCode.OUT_003,
                    "cannot traverse execution workspace for cleanup",
                    ex);
        }
    }

    private static ReportException invalidPath(String message) {
        return new ReportException(ReportErrorCode.OUT_001, message);
    }

    private static final class WorkspaceCleanupException extends RuntimeException {
        private WorkspaceCleanupException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
