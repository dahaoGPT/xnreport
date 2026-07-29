package com.xn.report.output;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class OutputPublisher {

    private static final ConcurrentHashMap<String, ReentrantLock> TARGET_LOCKS =
            new ConcurrentHashMap<String, ReentrantLock>();

    private final Path outputRoot;
    private final CollisionPolicy collisionPolicy;
    private final MoveStrategy moveStrategy;

    public OutputPublisher(Path outputRoot) {
        this(outputRoot, CollisionPolicy.VERSIONED);
    }

    public OutputPublisher(Path outputRoot, CollisionPolicy collisionPolicy) {
        this(outputRoot, collisionPolicy, OutputPublisher::defaultMove);
    }

    OutputPublisher(
            Path outputRoot,
            CollisionPolicy collisionPolicy,
            MoveStrategy moveStrategy) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.collisionPolicy =
                collisionPolicy == null ? CollisionPolicy.VERSIONED : collisionPolicy;
        this.moveStrategy = Objects.requireNonNull(moveStrategy, "moveStrategy");
        try {
            Files.createDirectories(this.outputRoot);
        } catch (IOException ex) {
            throw new ReportException(
                    ReportErrorCode.OUT_001, "cannot create output root", ex);
        }
    }

    public PublishedOutputs publish(
            Path sourceExcel, Path sourceWord, OutputTargets requestedTargets) {
        validateSource(sourceExcel, ".xlsx");
        validateSource(sourceWord, ".docx");
        Objects.requireNonNull(requestedTargets, "requestedTargets");
        Path requestedExcel = validateTarget(requestedTargets.getExcel(), ".xlsx");
        Path requestedWord = validateTarget(requestedTargets.getWord(), ".docx");
        requireSameBase(requestedExcel, requestedWord);

        String lockKey = requestedExcel.toString() + '\n' + requestedWord.toString();
        ReentrantLock lock = TARGET_LOCKS.computeIfAbsent(
                lockKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            OutputTargets resolved = resolveCollision(requestedExcel, requestedWord);
            return publishLocked(sourceExcel, sourceWord, resolved);
        } finally {
            lock.unlock();
        }
    }

    private PublishedOutputs publishLocked(
            Path sourceExcel, Path sourceWord, OutputTargets targets) {
        String token = UUID.randomUUID().toString();
        Path stagedExcel = publishingPath(targets.getExcel(), token);
        Path stagedWord = publishingPath(targets.getWord(), token);
        Path backupExcel = backupPath(targets.getExcel(), token);
        Path backupWord = backupPath(targets.getWord(), token);
        boolean excelBackedUp = false;
        boolean wordBackedUp = false;
        boolean excelPublished = false;
        boolean wordPublished = false;
        try {
            Files.copy(sourceExcel, stagedExcel);
            Files.copy(sourceWord, stagedWord);

            if (collisionPolicy == CollisionPolicy.OVERWRITE) {
                if (Files.exists(targets.getExcel())) {
                    moveStrategy.move(
                            targets.getExcel(), backupExcel,
                            StandardCopyOption.ATOMIC_MOVE);
                    excelBackedUp = true;
                }
                if (Files.exists(targets.getWord())) {
                    moveStrategy.move(
                            targets.getWord(), backupWord,
                            StandardCopyOption.ATOMIC_MOVE);
                    wordBackedUp = true;
                }
            }

            moveStrategy.move(
                    stagedExcel, targets.getExcel(), StandardCopyOption.ATOMIC_MOVE);
            excelPublished = true;
            moveStrategy.move(
                    stagedWord, targets.getWord(), StandardCopyOption.ATOMIC_MOVE);
            wordPublished = true;
            Files.deleteIfExists(backupExcel);
            Files.deleteIfExists(backupWord);
            return new PublishedOutputs(targets.getExcel(), targets.getWord());
        } catch (IOException ex) {
            IOException rollbackFailure = rollback(
                    targets,
                    backupExcel,
                    backupWord,
                    excelBackedUp,
                    wordBackedUp,
                    excelPublished,
                    wordPublished);
            if (rollbackFailure != null) {
                ex.addSuppressed(rollbackFailure);
            }
            throw new ReportException(
                    ReportErrorCode.OUT_003,
                    "atomic output pair publication failed",
                    ex);
        } finally {
            deleteQuietly(stagedExcel);
            deleteQuietly(stagedWord);
            deleteQuietly(backupExcel);
            deleteQuietly(backupWord);
        }
    }

    private IOException rollback(
            OutputTargets targets,
            Path backupExcel,
            Path backupWord,
            boolean excelBackedUp,
            boolean wordBackedUp,
            boolean excelPublished,
            boolean wordPublished) {
        IOException failure = null;
        if (wordPublished) {
            failure = deleteForRollback(targets.getWord(), failure);
        }
        if (excelPublished) {
            failure = deleteForRollback(targets.getExcel(), failure);
        }
        if (wordBackedUp) {
            failure = restoreBackup(backupWord, targets.getWord(), failure);
        }
        if (excelBackedUp) {
            failure = restoreBackup(backupExcel, targets.getExcel(), failure);
        }
        return failure;
    }

    private IOException restoreBackup(
            Path backup, Path target, IOException priorFailure) {
        try {
            Files.deleteIfExists(target);
            defaultMove(backup, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            return combine(priorFailure, ex);
        }
        return priorFailure;
    }

    private static IOException deleteForRollback(
            Path target, IOException priorFailure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            return combine(priorFailure, ex);
        }
        return priorFailure;
    }

    private OutputTargets resolveCollision(Path requestedExcel, Path requestedWord) {
        if (collisionPolicy == CollisionPolicy.FAIL) {
            if (Files.exists(requestedExcel) || Files.exists(requestedWord)) {
                throw new ReportException(
                        ReportErrorCode.OUT_002, "output file conflict");
            }
            return new OutputTargets(requestedExcel, requestedWord);
        }
        if (collisionPolicy == CollisionPolicy.OVERWRITE) {
            return new OutputTargets(requestedExcel, requestedWord);
        }

        String base = baseName(requestedExcel);
        int version = 0;
        while (true) {
            String suffix = version == 0 ? "" : "-" + version;
            Path excel = outputRoot.resolve(base + suffix + ".xlsx");
            Path word = outputRoot.resolve(base + suffix + ".docx");
            if (!Files.exists(excel) && !Files.exists(word)) {
                return new OutputTargets(excel, word);
            }
            version++;
        }
    }

    private Path validateTarget(Path target, String expectedExtension) {
        Objects.requireNonNull(target, "target");
        Path normalized = target.toAbsolutePath().normalize();
        if (!outputRoot.equals(normalized.getParent())) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "output target is outside configured output root");
        }
        if (!hasExtension(normalized, expectedExtension)) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "output target has wrong extension: " + expectedExtension);
        }
        return normalized;
    }

    private static void validateSource(Path source, String expectedExtension) {
        Objects.requireNonNull(source, "source");
        if (!Files.isRegularFile(source) || !hasExtension(source, expectedExtension)) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "publish source must be an existing " + expectedExtension + " file");
        }
    }

    private static boolean hasExtension(Path path, String extension) {
        Path fileName = path.getFileName();
        return fileName != null
                && fileName.toString().toLowerCase().endsWith(extension);
    }

    private static void requireSameBase(Path excel, Path word) {
        if (!baseName(excel).equals(baseName(word))) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "Excel and Word output targets must share the same base name");
        }
    }

    private static String baseName(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static Path publishingPath(Path target, String token) {
        return target.resolveSibling(
                baseName(target) + ".publishing-" + token + extension(target));
    }

    private static Path backupPath(Path target, String token) {
        return target.resolveSibling(
                baseName(target) + ".backup-" + token + extension(target));
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.'));
    }

    private static Path defaultMove(
            Path source, Path target, CopyOption... options) throws IOException {
        try {
            return Files.move(source, target, options);
        } catch (AtomicMoveNotSupportedException ex) {
            return Files.move(source, target);
        }
    }

    private static IOException combine(IOException prior, IOException next) {
        if (prior == null) {
            return next;
        }
        prior.addSuppressed(next);
        return prior;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The publish failure already carries the actionable exception.
        }
    }

    @FunctionalInterface
    interface MoveStrategy {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }
}
