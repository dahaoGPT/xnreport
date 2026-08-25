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

/**
 * 报表文件原子发布器。
 * <p>
 * 负责将临时工作空间中生成的 Excel 与 Word 文件原子地发布到目标输出目录中：
 * <ul>
 *   <li><b>多进程/多线程并发安全</b>：使用 JVM 细粒度 ReentrantLock 与跨进程 OS 级 FileLock 防止并发覆盖或文件写入竞争。</li>
 *   <li><b>冲突解决</b>：依据 {@link CollisionPolicy}（FAIL、OVERWRITE、VERSIONED）解决目标文件已存在的问题。</li>
 *   <li><b>双文件原子发布与回滚机制</b>：采用 Stage-Commit 协议（先复制到中间 staging 文件，原子重命名提交；覆盖模式下先建立 backup，发布任一文件失败时触发全量逆序回滚）。</li>
 *   <li><b>故障安全与工件报告</b>：在发布或回滚失败时记录未清理工件与警告，绝不丢失关键上下文。</li>
 * </ul>
 * </p>
 */
public final class OutputPublisher {

    /** JVM 内部目标路径互斥锁注册表。 */
    private static final ConcurrentHashMap<String, LockEntry> TARGET_LOCKS =
            new ConcurrentHashMap<String, LockEntry>();

    /** 输出根目录。 */
    private final Path outputRoot;

    /** 冲突解决策略。 */
    private final CollisionPolicy collisionPolicy;

    /** 文件移动策略。 */
    private final MoveStrategy moveStrategy;

    /** 文件删除策略。 */
    private final DeleteStrategy deleteStrategy;

    /** 跨进程文件加锁策略。 */
    private final LockStrategy lockStrategy;

    /** 文件拷贝策略。 */
    private final CopyStrategy copyStrategy;

    /**
     * 默认构造函数（使用 VERSIONED 版本化冲突策略）。
     *
     * @param outputRoot 输出根目录
     */
    public OutputPublisher(Path outputRoot) {
        this(outputRoot, CollisionPolicy.VERSIONED);
    }

    /**
     * 指定冲突策略构造发布器。
     *
     * @param outputRoot 输出根目录
     * @param collisionPolicy 冲突解决策略
     */
    public OutputPublisher(Path outputRoot, CollisionPolicy collisionPolicy) {
        this(
                outputRoot,
                collisionPolicy,
                OutputPublisher::defaultMove,
                Files::deleteIfExists,
                OutputPublisher::acquireDefaultProcessLock,
                Files::copy);
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
                OutputPublisher::acquireDefaultProcessLock,
                Files::copy);
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
                OutputPublisher::acquireDefaultProcessLock,
                Files::copy);
    }

    OutputPublisher(
            Path outputRoot,
            CollisionPolicy collisionPolicy,
            MoveStrategy moveStrategy,
            DeleteStrategy deleteStrategy,
            LockStrategy lockStrategy) {
        this(
                outputRoot,
                collisionPolicy,
                moveStrategy,
                deleteStrategy,
                lockStrategy,
                Files::copy);
    }

    OutputPublisher(
            Path outputRoot,
            CollisionPolicy collisionPolicy,
            MoveStrategy moveStrategy,
            DeleteStrategy deleteStrategy,
            LockStrategy lockStrategy,
            CopyStrategy copyStrategy) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.collisionPolicy =
                collisionPolicy == null ? CollisionPolicy.VERSIONED : collisionPolicy;
        this.moveStrategy = Objects.requireNonNull(moveStrategy, "moveStrategy");
        this.deleteStrategy = Objects.requireNonNull(deleteStrategy, "deleteStrategy");
        this.lockStrategy = Objects.requireNonNull(lockStrategy, "lockStrategy");
        this.copyStrategy = Objects.requireNonNull(copyStrategy, "copyStrategy");
        try {
            Files.createDirectories(this.outputRoot);
        } catch (IOException ex) {
            throw new ReportException(
                    ReportErrorCode.OUT_001, "cannot create output root", ex);
        }
    }

    /**
     * 将临时文件中的 Excel 和 Word 原子的发布到目标路径。
     *
     * @param sourceExcel 源临时 Excel 文件路径
     * @param sourceWord 源临时 Word 文件路径
     * @param requestedTargets 期望的目标路径对
     * @return 实际发布的输出文件结果
     * @throws ReportException 发布失败或发生冲突时抛出
     */
    public PublishedOutputs publish(
            Path sourceExcel, Path sourceWord, OutputTargets requestedTargets) {
        // 校验源文件和目标文件合法性
        validateSource(sourceExcel, ".xlsx");
        validateSource(sourceWord, ".docx");
        Objects.requireNonNull(requestedTargets, "requestedTargets");
        Path requestedExcel = validateTarget(requestedTargets.getExcel(), ".xlsx");
        Path requestedWord = validateTarget(requestedTargets.getWord(), ".docx");
        requireSameBase(requestedExcel, requestedWord);

        // 获取 JVM 锁与跨进程锁
        String lockKey = normalizedLockKey(requestedExcel, requestedWord);
        JvmLockHandle jvmLock = acquireJvmLock(lockKey);
        PublicationLock processLock = null;
        PublishedOutputs result = null;
        RuntimeException publicationFailure = null;
        try {
            try {
                processLock = lockStrategy.acquire(lockKey);
                // 根据冲突策略解析实际目标路径
                OutputTargets resolved =
                        resolveCollision(requestedExcel, requestedWord);
                // 执行原子发布流程
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
                    } catch (IOException | RuntimeException ex) {
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

    /**
     * 在持有并发锁的前提下执行双文件 Stage-Commit 流程。
     */
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
            // 1. 复制到中间暂存文件
            copyStrategy.copy(sourceExcel, stagedExcel);
            copyStrategy.copy(sourceWord, stagedWord);

            // 2. 覆盖模式下先建立原有文件备份
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

            // 3. 原子移动暂存文件至最终目标
            moveStrategy.move(
                    stagedExcel, targets.getExcel(), StandardCopyOption.ATOMIC_MOVE);
            excelPublished = true;
            moveStrategy.move(
                    stagedWord, targets.getWord(), StandardCopyOption.ATOMIC_MOVE);
            wordPublished = true;

            // 4. 清理备份和暂存文件
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
        } catch (IOException | RuntimeException ex) {
            // 发生异常时执行双文件逆序回滚与备份恢复
            RollbackOutcome rollback = rollback(
                    targets,
                    backupExcel,
                    backupWord,
                    excelBackedUp,
                    wordBackedUp,
                    excelPublished,
                    wordPublished);
            Throwable failure = ex;
            if (rollback.failure != null) {
                addSuppressedIfDistinct(failure, rollback.failure);
            }
            CleanupOutcome cleanup = cleanupArtifacts(
                    "staged cleanup failed before commit",
                    stagedExcel,
                    stagedWord);
            if (cleanup.failure != null) {
                addSuppressedIfDistinct(failure, cleanup.failure);
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
                    failure);
        }
    }

    /**
     * 执行逆序回滚：删除已发布的半成品，恢复覆盖前的旧文件备份。
     */
    private RollbackOutcome rollback(
            OutputTargets targets,
            Path backupExcel,
            Path backupWord,
            boolean excelBackedUp,
            boolean wordBackedUp,
            boolean excelPublished,
            boolean wordPublished) {
        Throwable failure = null;
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

    /** 恢复备份文件。 */
    private Throwable restoreBackup(
            Path backup, Path target, Throwable priorFailure) {
        try {
            Files.deleteIfExists(target);
            moveStrategy.move(
                    backup, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException ex) {
            return combineFailure(priorFailure, ex);
        }
        return priorFailure;
    }

    /** 回滚时删除已发布的目标文件。 */
    private static Throwable deleteForRollback(
            Path target, Throwable priorFailure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException | RuntimeException ex) {
            return combineFailure(priorFailure, ex);
        }
        return priorFailure;
    }

    /**
     * 根据冲突策略解析实际可用的目标路径（VERSIONED 模式下递增序号）。
     */
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

        // VERSIONED 策略：寻找未被占用的版本序号后缀（如 -1, -2）
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

    private CleanupOutcome cleanupArtifacts(String warningPrefix, Path... artifacts) {
        Throwable failure = null;
        List<Path> retained = new ArrayList<Path>();
        for (Path artifact : artifacts) {
            try {
                if (!Files.exists(artifact)) {
                    continue;
                }
                deleteStrategy.delete(artifact);
                if (Files.exists(artifact)) {
                    retained.add(artifact.toAbsolutePath().normalize());
                }
            } catch (IOException | RuntimeException ex) {
                failure = combineFailure(failure, ex);
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
            boolean artifactRetained;
            try {
                artifactRetained = Files.exists(backup);
            } catch (RuntimeException ignored) {
                artifactRetained = true;
            }
            if (artifactRetained) {
                if (retained.length() > 0) {
                    retained.append(", ");
                }
                retained.append(backup.toAbsolutePath().normalize());
            }
        }
        return retained.toString();
    }

    /**
     * 获取操作系统级跨进程文件独占锁。
     */
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
            try {
                channel.close();
            } catch (IOException | RuntimeException closeFailure) {
                addSuppressedIfDistinct(ex, closeFailure);
            }
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
    interface CopyStrategy {
        Path copy(Path source, Path target, CopyOption... options) throws IOException;
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
            Throwable failure = null;
            try {
                lock.release();
            } catch (IOException | RuntimeException ex) {
                failure = ex;
            }
            try {
                channel.close();
            } catch (IOException | RuntimeException ex) {
                failure = combineFailure(failure, ex);
            }
            if (failure != null) {
                if (failure instanceof IOException) {
                    throw (IOException) failure;
                }
                throw (RuntimeException) failure;
            }
        }
    }

    private static final class RollbackOutcome {
        private final Throwable failure;

        private RollbackOutcome(Throwable failure) {
            this.failure = failure;
        }
    }

    private static final class CleanupOutcome {
        private final List<String> warnings;
        private final List<Path> artifactPaths;
        private final Throwable failure;

        private CleanupOutcome(
                List<String> warnings,
                List<Path> artifactPaths,
                Throwable failure) {
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

    private static String describe(Throwable failure) {
        StringBuilder description = new StringBuilder();
        appendDescription(description, failure);
        for (Throwable suppressed : failure.getSuppressed()) {
            description.append("; ");
            appendDescription(description, suppressed);
        }
        return description.toString();
    }

    private static void appendDescription(
            StringBuilder description, Throwable failure) {
        String message = failure.getMessage();
        description.append(message == null
                ? failure.getClass().getSimpleName()
                : message);
    }

    private static Throwable combineFailure(Throwable prior, Throwable next) {
        if (prior == null) {
            return next;
        }
        addSuppressedIfDistinct(prior, next);
        return prior;
    }

    private static void addSuppressedIfDistinct(
            Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }
}
