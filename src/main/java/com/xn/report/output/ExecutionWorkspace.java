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

public final class ExecutionWorkspace implements AutoCloseable {

    private final String executionId;
    private final Path tempRoot;
    private final Path root;
    private final Path realRoot;
    private final Path excelDirectory;
    private final Path wordDirectory;
    private final Path chartsDirectory;
    private final DeleteStrategy deleteStrategy;
    private boolean closed;

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

    public static ExecutionWorkspace create(Path configuredTempRoot) {
        return create(
                configuredTempRoot,
                UUID.randomUUID().toString(),
                Files::createDirectory,
                Files::deleteIfExists);
    }

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
            if (!realRoot.getParent().equals(realTempRoot)) {
                throw new IOException("workspace real path escaped temp root");
            }
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

    @FunctionalInterface
    interface DirectoryCreator {
        Path create(Path path) throws IOException;
    }

    @FunctionalInterface
    interface DeleteStrategy {
        boolean delete(Path path) throws IOException;
    }
}
