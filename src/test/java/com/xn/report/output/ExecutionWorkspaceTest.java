package com.xn.report.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.error.ReportException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionWorkspaceTest {

    @TempDir
    Path temp;

    @Test
    void createsRandomOwnedLayoutAndCleansOnlyItsOwnDirectory() throws Exception {
        Path sibling = Files.write(
                temp.resolve("keep.txt"), "keep".getBytes(StandardCharsets.UTF_8));
        ExecutionWorkspace first = ExecutionWorkspace.create(temp);
        ExecutionWorkspace second = ExecutionWorkspace.create(temp);
        assertThat(first.getExecutionId()).isNotEqualTo(second.getExecutionId());
        assertThat(first.getExcelDirectory()).isDirectory();
        assertThat(first.getWordDirectory()).isDirectory();
        assertThat(first.getChartsDirectory()).isDirectory();
        Files.write(first.getExcelDirectory().resolve("report.xlsx"), new byte[] {1});

        first.close();

        assertThat(first.getRoot()).doesNotExist();
        assertThat(second.getRoot()).isDirectory();
        assertThat(sibling).exists();
        second.close();
    }

    @Test
    void rejectsResolutionAndCleanupOutsideOwnedWorkspace() throws Exception {
        try (ExecutionWorkspace workspace = ExecutionWorkspace.create(temp)) {
            assertThatThrownBy(() -> workspace.resolve("../outside.txt"))
                    .isInstanceOf(ReportException.class)
                    .hasMessageContaining("workspace");
            assertThatThrownBy(() -> workspace.assertOwned(temp.resolve("other")))
                    .isInstanceOf(ReportException.class);
        }
    }

    @Test
    void cleansPartialWorkspaceWhenDirectoryCreationFails() throws Exception {
        AtomicInteger created = new AtomicInteger();
        ExecutionWorkspace.DirectoryCreator creator = path -> {
            Files.createDirectory(path);
            if (created.incrementAndGet() == 3) {
                throw new java.io.IOException("word directory failure");
            }
            return path;
        };

        assertThatThrownBy(() -> ExecutionWorkspace.create(
                temp, "fixed-execution", creator, Files::deleteIfExists))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("create");
        assertThat(temp.resolve("fixed-execution")).doesNotExist();
    }

    @Test
    void cleanupAttemptsEveryEntryAndCanBeRetriedAfterFailure() throws Exception {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        ExecutionWorkspace.DeleteStrategy deleter = path -> {
            if (path.getFileName().toString().equals("locked.txt")
                    && failOnce.compareAndSet(true, false)) {
                throw new java.io.IOException("locked");
            }
            return Files.deleteIfExists(path);
        };
        ExecutionWorkspace workspace = ExecutionWorkspace.create(
                temp, "retry-execution", Files::createDirectory, deleter);
        Path locked = Files.write(workspace.resolve("locked.txt"), new byte[] {1});
        Path removable = Files.write(workspace.resolve("removable.txt"), new byte[] {2});

        assertThatThrownBy(workspace::close)
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("clean");
        assertThat(locked).exists();
        assertThat(removable).doesNotExist();

        workspace.close();
        assertThat(workspace.getRoot()).doesNotExist();
    }

    @Test
    void rejectsNonexistentDescendantBelowSymlinkEscapingWorkspace() throws Exception {
        ExecutionWorkspace workspace = ExecutionWorkspace.create(temp);
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Path link = workspace.getRoot().resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException ex) {
            workspace.close();
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + ex);
        }
        try {
            assertThatThrownBy(() -> workspace.resolve("link/not-created.txt"))
                    .isInstanceOf(ReportException.class)
                    .hasMessageContaining("outside");
        } finally {
            workspace.close();
        }
    }
}
