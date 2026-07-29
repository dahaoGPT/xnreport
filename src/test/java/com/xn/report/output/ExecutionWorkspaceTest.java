package com.xn.report.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.error.ReportException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
