package com.xn.report.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputPublisherTest {

    @TempDir
    Path temp;

    @Test
    void defaultsToVersionedAndUsesOneVersionedBaseForThePair() throws Exception {
        Path output = Files.createDirectory(temp.resolve("output"));
        Files.write(output.resolve("report.xlsx"), bytes("old-x"));
        Path excel = source("new.xlsx", "new-x");
        Path word = source("new.docx", "new-w");

        PublishedOutputs result = new OutputPublisher(output).publish(
                excel, word, new OutputTargets(
                        output.resolve("report.xlsx"), output.resolve("report.docx")));

        assertThat(result.getExcel().getFileName().toString()).isEqualTo("report-1.xlsx");
        assertThat(result.getWord().getFileName().toString()).isEqualTo("report-1.docx");
        assertThat(read(result.getExcel())).isEqualTo("new-x");
        assertThat(read(result.getWord())).isEqualTo("new-w");
    }

    @Test
    void failPolicyLeavesExistingPairUntouched() throws Exception {
        Path output = Files.createDirectory(temp.resolve("fail-output"));
        Path oldExcel = Files.write(output.resolve("report.xlsx"), bytes("old-x"));
        Path oldWord = Files.write(output.resolve("report.docx"), bytes("old-w"));
        OutputPublisher publisher = new OutputPublisher(output, CollisionPolicy.FAIL);

        assertThatThrownBy(() -> publisher.publish(
                source("fail-new.xlsx", "new-x"),
                source("fail-new.docx", "new-w"),
                new OutputTargets(oldExcel, oldWord)))
                .isInstanceOf(ReportException.class)
                .extracting(e -> ((ReportException) e).getErrorCode())
                .isEqualTo(ReportErrorCode.OUT_002);
        assertThat(read(oldExcel)).isEqualTo("old-x");
        assertThat(read(oldWord)).isEqualTo("old-w");
    }

    @Test
    void overwriteReplacesBothFiles() throws Exception {
        Path output = Files.createDirectory(temp.resolve("overwrite-output"));
        Path oldExcel = Files.write(output.resolve("report.xlsx"), bytes("old-x"));
        Path oldWord = Files.write(output.resolve("report.docx"), bytes("old-w"));

        PublishedOutputs result = new OutputPublisher(output, CollisionPolicy.OVERWRITE)
                .publish(
                        source("overwrite-new.xlsx", "new-x"),
                        source("overwrite-new.docx", "new-w"),
                        new OutputTargets(oldExcel, oldWord));

        assertThat(result.getExcel()).isEqualTo(oldExcel);
        assertThat(read(oldExcel)).isEqualTo("new-x");
        assertThat(read(oldWord)).isEqualTo("new-w");
    }

    @Test
    void coordinationLockDoesNotPollutePublishedOutputDirectory() throws Exception {
        Path output = Files.createDirectory(temp.resolve("clean-output"));

        new OutputPublisher(output).publish(
                source("clean.xlsx", "x"),
                source("clean.docx", "w"),
                new OutputTargets(
                        output.resolve("report.xlsx"),
                        output.resolve("report.docx")));

        try (java.util.stream.Stream<Path> paths = Files.list(output)) {
            assertThat(paths.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("report.xlsx", "report.docx");
        }
    }

    @Test
    void secondFinalMoveFailureRollsBackNewFirstFile() throws Exception {
        Path output = Files.createDirectory(temp.resolve("rollback-output"));
        AtomicInteger finalMoves = new AtomicInteger();
        OutputPublisher.MoveStrategy failingSecondFinalMove =
                (source, target, options) -> {
                    if (!target.getFileName().toString().contains(".publishing-")
                            && !target.getFileName().toString().contains(".backup-")
                            && finalMoves.incrementAndGet() == 2) {
                        throw new IOException("second final move failed");
                    }
                    return Files.move(source, target, options);
                };
        OutputPublisher publisher = new OutputPublisher(
                output, CollisionPolicy.FAIL, failingSecondFinalMove);
        OutputTargets targets = new OutputTargets(
                output.resolve("report.xlsx"), output.resolve("report.docx"));

        assertThatThrownBy(() -> publisher.publish(
                source("rollback.xlsx", "x"), source("rollback.docx", "w"), targets))
                .isInstanceOf(ReportException.class)
                .extracting(e -> ((ReportException) e).getErrorCode())
                .isEqualTo(ReportErrorCode.OUT_003);
        assertThat(targets.getExcel()).doesNotExist();
        assertThat(targets.getWord()).doesNotExist();
    }

    @Test
    void overwriteFailureRestoresBothExistingFiles() throws Exception {
        Path output = Files.createDirectory(temp.resolve("restore-output"));
        Path oldExcel = Files.write(output.resolve("report.xlsx"), bytes("old-x"));
        Path oldWord = Files.write(output.resolve("report.docx"), bytes("old-w"));
        AtomicInteger finalMoves = new AtomicInteger();
        OutputPublisher.MoveStrategy failingSecondFinalMove =
                (source, target, options) -> {
                    String targetName = target.getFileName().toString();
                    if (!targetName.contains(".publishing-")
                            && !targetName.contains(".backup-")
                            && finalMoves.incrementAndGet() == 2) {
                        throw new IOException("second final move failed");
                    }
                    return Files.move(source, target, options);
                };

        assertThatThrownBy(() -> new OutputPublisher(
                output, CollisionPolicy.OVERWRITE, failingSecondFinalMove).publish(
                source("restore.xlsx", "new-x"),
                source("restore.docx", "new-w"),
                new OutputTargets(oldExcel, oldWord)))
                .isInstanceOf(ReportException.class);
        assertThat(read(oldExcel)).isEqualTo("old-x");
        assertThat(read(oldWord)).isEqualTo("old-w");
    }

    @Test
    void rejectsWrongExtensionsAndTargetsOutsideOutputRoot() throws Exception {
        Path output = Files.createDirectory(temp.resolve("safe-output"));
        OutputPublisher publisher = new OutputPublisher(output);

        assertThatThrownBy(() -> publisher.publish(
                source("bad.xlsx", "x"), source("bad.docx", "w"),
                new OutputTargets(output.resolve("report.docx"), output.resolve("report.xlsx"))))
                .isInstanceOf(ReportException.class)
                .extracting(e -> ((ReportException) e).getErrorCode())
                .isEqualTo(ReportErrorCode.OUT_001);
        assertThatThrownBy(() -> publisher.publish(
                source("outside.xlsx", "x"), source("outside.docx", "w"),
                new OutputTargets(temp.resolve("outside.xlsx"), output.resolve("report.docx"))))
                .isInstanceOf(ReportException.class);
    }

    @Test
    void concurrentVersionedPublishesNeverSplitOrOverwritePairs() throws Exception {
        Path output = Files.createDirectory(temp.resolve("concurrent-output"));
        OutputPublisher publisher = new OutputPublisher(output);
        OutputTargets targets = new OutputTargets(
                output.resolve("report.xlsx"), output.resolve("report.docx"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PublishedOutputs> first = executor.submit(() -> {
                start.await();
                return publisher.publish(
                        source("c1.xlsx", "x1"), source("c1.docx", "w1"), targets);
            });
            Future<PublishedOutputs> second = executor.submit(() -> {
                start.await();
                return publisher.publish(
                        source("c2.xlsx", "x2"), source("c2.docx", "w2"), targets);
            });
            start.countDown();
            PublishedOutputs one = first.get();
            PublishedOutputs two = second.get();

            assertThat(one.getExcel()).isNotEqualTo(two.getExcel());
            assertThat(one.getWord()).isNotEqualTo(two.getWord());
            assertThat(read(one.getExcel()).substring(1))
                    .isEqualTo(read(one.getWord()).substring(1));
            assertThat(read(two.getExcel()).substring(1))
                    .isEqualTo(read(two.getWord()).substring(1));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void backupCleanupFailureDoesNotRollbackCommittedNewPair() throws Exception {
        Path output = Files.createDirectory(temp.resolve("cleanup-output"));
        Path oldExcel = Files.write(output.resolve("report.xlsx"), bytes("old-x"));
        Path oldWord = Files.write(output.resolve("report.docx"), bytes("old-w"));
        OutputPublisher.DeleteStrategy failingBackupCleanup = path -> {
            if (path.getFileName().toString().contains(".backup-")) {
                throw new IOException("cannot delete " + path);
            }
            return Files.deleteIfExists(path);
        };
        OutputPublisher publisher = new OutputPublisher(
                output,
                CollisionPolicy.OVERWRITE,
                OutputPublisher::defaultMove,
                failingBackupCleanup);

        assertThatThrownBy(() -> publisher.publish(
                source("cleanup.xlsx", "new-x"),
                source("cleanup.docx", "new-w"),
                new OutputTargets(oldExcel, oldWord)))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("already committed")
                .hasMessageContaining(".backup-");
        assertThat(read(oldExcel)).isEqualTo("new-x");
        assertThat(read(oldWord)).isEqualTo("new-w");
        try (java.util.stream.Stream<Path> paths = Files.list(output)) {
            assertThat(paths
                    .filter(path -> path.getFileName().toString()
                            .contains(".backup-"))
                    .count()).isEqualTo(2);
        }
    }

    @Test
    void restoreFailureRetainsBackupAndReportsItsPath() throws Exception {
        Path output = Files.createDirectory(temp.resolve("restore-failure-output"));
        Path oldExcel = Files.write(output.resolve("report.xlsx"), bytes("old-x"));
        Path oldWord = Files.write(output.resolve("report.docx"), bytes("old-w"));
        AtomicInteger finalMoves = new AtomicInteger();
        OutputPublisher.MoveStrategy failingFinalAndRestore =
                (source, target, options) -> {
                    String sourceName = source.getFileName().toString();
                    String targetName = target.getFileName().toString();
                    if (sourceName.contains(".backup-")) {
                        throw new IOException("cannot restore " + source);
                    }
                    if (!targetName.contains(".backup-")
                            && finalMoves.incrementAndGet() == 2) {
                        throw new IOException("second final move failed");
                    }
                    return Files.move(source, target, options);
                };

        assertThatThrownBy(() -> new OutputPublisher(
                output, CollisionPolicy.OVERWRITE, failingFinalAndRestore).publish(
                source("restore-failure.xlsx", "new-x"),
                source("restore-failure.docx", "new-w"),
                new OutputTargets(oldExcel, oldWord)))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("backup")
                .hasMessageContaining(output.toString());
        try (java.util.stream.Stream<Path> paths = Files.list(output)) {
            assertThat(paths
                    .filter(path -> path.getFileName().toString()
                            .contains(".backup-"))
                    .count()).isEqualTo(2);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsCaseVariantsShareOneJvmTargetLockAcrossPublishers() throws Exception {
        Path output = Files.createDirectory(temp.resolve("case-lock-output"));
        CountDownLatch firstFinalMove = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        OutputPublisher.MoveStrategy blockingFirstMove =
                (source, target, options) -> {
                    if (!target.getFileName().toString().contains(".backup-")) {
                        firstFinalMove.countDown();
                        try {
                            releaseFirst.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IOException("interrupted", ex);
                        }
                    }
                    return Files.move(source, target, options);
                };
        OutputPublisher firstPublisher = new OutputPublisher(
                output, CollisionPolicy.VERSIONED, blockingFirstMove);
        OutputPublisher secondPublisher = new OutputPublisher(
                output, CollisionPolicy.VERSIONED);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PublishedOutputs> first = executor.submit(() ->
                    firstPublisher.publish(
                            source("case1.xlsx", "x1"),
                            source("case1.docx", "w1"),
                            new OutputTargets(
                                    output.resolve("report.xlsx"),
                                    output.resolve("report.docx"))));
            firstFinalMove.await();
            Future<PublishedOutputs> second = executor.submit(() ->
                    secondPublisher.publish(
                            source("case2.xlsx", "x2"),
                            source("case2.docx", "w2"),
                            new OutputTargets(
                                    output.resolve("REPORT.xlsx"),
                                    output.resolve("REPORT.docx"))));
            Thread.sleep(200L);
            assertThat(second).isNotDone();
            releaseFirst.countDown();
            assertThat(first.get().getExcel()).exists();
            assertThat(second.get().getExcel()).exists();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        assertThat(OutputPublisher.jvmLockRegistrySize()).isZero();
    }

    private Path source(String name, String content) throws IOException {
        return Files.write(temp.resolve(name), bytes(content));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
