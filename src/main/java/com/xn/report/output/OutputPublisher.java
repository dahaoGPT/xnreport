package com.xn.report.output;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class OutputPublisher {

    private static final ConcurrentHashMap<String, LockEntry> TARGET_LOCKS =
            new ConcurrentHashMap<String, LockEntry>();

    private final Path outputRoot;
    private final CollisionPolicy collisionPolicy;
    private final MoveStrategy moveStrategy;
    private final DeleteStrategy deleteStrategy;
    private final LockStrategy lockStrategy;

    public OutputPublisher(Path outputRoot) {
        this(outputRoot, CollisionPolicy.VERSIONED);
    }

    public OutputPublisher(Path outputRoot, CollisionPolicy collisionPolicy) {
        this(
                outputRoot,
                collisionPolicy,
                OutputPublisher::defaultMove,
                Files::deleteIfExists,
                OutputPublisher::acquireDefaultProcessLock);
    }

    OutputPublisher(
            Path outputRoot,
            CollisionPolicy collisionPolicy,
            MoveStrategy moveStrategy) {
        this(
                outputRoot,
                collisionPolicy,
                moveStrategy,
                Files::deleteIfExists,
                OutputPublisher::acquireDefaultProcessLock);
    }

    OutputPublisher(
            Path outputRoot,
            CollisionPolicy collisionPolicy,
            MoveStrategy moveStrategy,
            DeleteStrategy deleteStrategy) {
        this(
                outputRoot,
                collisionPolicy,
                moveStrategy,
                deleteStrategy,
                OutputPublisher::acquireDefaultProcessLock);
    }

    OutputPublisher(
            Path outputRoot,
            CollisionPolicy collisionPolicy,
            MoveStrategy moveStrategy,
            DeleteStrategy deleteStrategy,
            LockStrategy lockStrategy) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.collisionPolicy =
                collisionPolicy == null ? CollisionPolicy.VERSIONED : collisionPolicy;
        this.moveStrategy = Objects.requireNonNull(moveStrategy, "moveStrategy");
        this.deleteStrategy = Objects.requireNonNull(deleteStrategy, "deleteStrategy");
        this.lockStrategy = Objects.requireNonNull(lockStrategy, "lockStrategy");
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

        String lockKey = normalizedLockKey(requestedExcel, requestedWord);
        JvmLockHandle jvmLock = acquireJvmLock(lockKey);
        PublicationLock processLock = null;
        PublishedOutputs result = null;
        RuntimeException publicationFailure = null;
        try {
            try {
                processLock = lockStrategy.acquire(lockKey);
                OutputTargets resolved =
                        resolveCollision(requestedExcel, requestedWord);
                result = publishLocked(sourceExcel, sourceWord, resolved);
            } catch (IOException ex) {
                publicationFailure = new ReportException(
                        ReportErrorCode.OUT_003,
                        "cannot acquire output publication lock",
                        ex);
            } catch (RuntimeException ex) {
                publicationFailure = ex;
            } finally {
                if (processLock != null) {
                    try {
                        processLock.close();
                    } catch (IOException ex) {
                        if (result != null) {
                            result = result.withWarning(
                                    "publication lock cleanup failed after commit: "
                                            + describe(ex));
                        } else if (publicationFailure != null) {
                            publicationFailure.addSuppressed(ex);
                        } else {
                            publicationFailure = new ReportException(
                                    ReportErrorCode.OUT_003,
                                    "cannot release output publication lock",
                                    ex);
                        }
                    }
                }
            }
            if (publicationFailure != null) {
                throw publicationFailure;
            }
            return result;
        } finally {
            jvmLock.close();
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
            CleanupOutcome cleanup = cleanupArtifacts(
                    "backup cleanup failed after commit",
                    backupExcel,
                    backupWord,
                    stagedExcel,
                    stagedWord);
            return new PublishedOutputs(
                    targets.getExcel(),
                    targets.getWord(),
                    cleanup.warnings,
                    cleanup.artifactPaths);
        } catch (IOException ex) {
            RollbackOutcome rollback = rollback(
                    targets,
                    backupExcel,
                    backupWord,
                    excelBackedUp,
                    wordBackedUp,
                    excelPublished,
                    wordPublished);
            if (rollback.failure != null) {
                ex.addSuppressed(rollback.failure);
            }
            CleanupOutcome cleanup = cleanupArtifacts(
                    "staged cleanup failed before commit",
                    stagedExcel,
                    stagedWord);
            if (cleanup.failure != null) {
                ex.addSuppressed(cleanup.failure);
            }
            String retained = retainedArtifacts(
                    backupExcel, backupWord, stagedExcel, stagedWord);
            throw new ReportException(
                    ReportErrorCode.OUT_003,
                    "atomic output pair publication failed"
                            + (retained.isEmpty()
                                    ? ""
                                    : "; recovery backup retained or cleanup artifact at "
                                            + retained)
                            + (cleanup.warnings.isEmpty()
                                    ? ""
                                    : "; " + cleanup.warnings.get(0)),
                    ex);
        }
    }

    private RollbackOutcome rollback(
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
        return new RollbackOutcome(failure);
    }

    private IOException restoreBackup(
            Path backup, Path target, IOException priorFailure) {
        try {
            Files.deleteIfExists(target);
            moveStrategy.move(
                    backup, target, StandardCopyOption.ATOMIC_MOVE);
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

    static Path defaultMove(
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

    private CleanupOutcome cleanupArtifacts(String warningPrefix, Path... artifacts) {
        IOException failure = null;
        List<Path> retained = new ArrayList<Path>();
        for (Path artifact : artifacts) {
            if (!Files.exists(artifact)) {
                continue;
            }
            try {
                deleteStrategy.delete(artifact);
            } catch (IOException ex) {
                failure = combine(failure, ex);
                retained.add(artifact.toAbsolutePath().normalize());
                continue;
            }
            if (Files.exists(artifact)) {
                retained.add(artifact.toAbsolutePath().normalize());
            }
        }
        if (retained.isEmpty()) {
            return CleanupOutcome.empty();
        }
        String warning = warningPrefix + ": " + retained
                + (failure == null ? "" : "; " + describe(failure));
        return new CleanupOutcome(
                Collections.singletonList(warning), retained, failure);
    }

    private static String retainedArtifacts(Path... backups) {
        StringBuilder retained = new StringBuilder();
        for (Path backup : backups) {
            if (Files.exists(backup)) {
                if (retained.length() > 0) {
                    retained.append(", ");
                }
                retained.append(backup.toAbsolutePath().normalize());
            }
        }
        return retained.toString();
    }

    static PublicationLock acquireDefaultProcessLock(String key) throws IOException {
        Path lockRoot = Paths.get(
                System.getProperty("java.io.tmpdir"),
                "xnreport-publish-locks").toAbsolutePath().normalize();
        Files.createDirectories(lockRoot);
        Path lockFile = lockRoot.resolve(
                ".xnreport-publish-" + sha256(key) + ".lck");
        FileChannel channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        try {
            return new CrossProcessLock(channel, channel.lock());
        } catch (IOException | RuntimeException ex) {
            channel.close();
            throw ex;
        }
    }

    private static String normalizedLockKey(Path excel, Path word) {
        String key = excel.toAbsolutePath().normalize().toString()
                + '\n'
                + word.toAbsolutePath().normalize().toString();
        return isWindows() ? key.toLowerCase(Locale.ROOT) : key;
    }

    private static boolean isWindows() {
        return java.io.File.separatorChar == '\\';
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JRE", ex);
        }
    }

    private static JvmLockHandle acquireJvmLock(String key) {
        LockEntry entry = TARGET_LOCKS.compute(key, (ignored, current) -> {
            LockEntry result = current == null ? new LockEntry() : current;
            result.references++;
            return result;
        });
        entry.lock.lock();
        return new JvmLockHandle(key, entry);
    }

    static int jvmLockRegistrySize() {
        return TARGET_LOCKS.size();
    }

    @FunctionalInterface
    interface MoveStrategy {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }

    @FunctionalInterface
    interface DeleteStrategy {
        boolean delete(Path path) throws IOException;
    }

    @FunctionalInterface
    interface LockStrategy {
        PublicationLock acquire(String key) throws IOException;
    }

    @FunctionalInterface
    interface PublicationLock extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    private static final class JvmLockHandle implements AutoCloseable {
        private final String key;
        private final LockEntry entry;
        private boolean closed;

        private JvmLockHandle(String key, LockEntry entry) {
            this.key = key;
            this.entry = entry;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entry.lock.unlock();
            TARGET_LOCKS.computeIfPresent(key, (ignored, current) -> {
                if (current != entry) {
                    return current;
                }
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    private static final class CrossProcessLock implements PublicationLock {
        private final FileChannel channel;
        private final FileLock lock;

        private CrossProcessLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                lock.release();
            } catch (IOException ex) {
                failure = ex;
            }
            try {
                channel.close();
            } catch (IOException ex) {
                failure = combine(failure, ex);
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class RollbackOutcome {
        private final IOException failure;

        private RollbackOutcome(IOException failure) {
            this.failure = failure;
        }
    }

    private static final class CleanupOutcome {
        private final List<String> warnings;
        private final List<Path> artifactPaths;
        private final IOException failure;

        private CleanupOutcome(
                List<String> warnings,
                List<Path> artifactPaths,
                IOException failure) {
            this.warnings = warnings;
            this.artifactPaths = artifactPaths;
            this.failure = failure;
        }

        private static CleanupOutcome empty() {
            return new CleanupOutcome(
                    Collections.<String>emptyList(),
                    Collections.<Path>emptyList(),
                    null);
        }
    }

    private static String describe(IOException failure) {
        StringBuilder description = new StringBuilder();
        appendDescription(description, failure);
        for (Throwable suppressed : failure.getSuppressed()) {
            if (suppressed instanceof IOException) {
                description.append("; ");
                appendDescription(description, (IOException) suppressed);
            }
        }
        return description.toString();
    }

    private static void appendDescription(
            StringBuilder description, IOException failure) {
        String message = failure.getMessage();
        description.append(message == null
                ? failure.getClass().getSimpleName()
                : message);
    }
}
