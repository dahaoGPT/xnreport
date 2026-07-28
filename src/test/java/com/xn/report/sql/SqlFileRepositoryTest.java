package com.xn.report.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.RootPathPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlFileRepositoryTest {

    @TempDir
    Path root;

    @Test
    void readsUtf8AndRemovesBomWithoutChangingLineBreaks() throws Exception {
        Path sqlFile = root.resolve("monthly.sql");
        String sql = "\uFEFFSELECT '研发'\r\nFROM report\r\n";
        Files.write(sqlFile, sql.getBytes(StandardCharsets.UTF_8));

        String loaded = repository().read("monthly.sql");

        assertThat(loaded).isEqualTo("SELECT '研发'\r\nFROM report\r\n");
    }

    @Test
    void rejectsPathsOutsideConfiguredRoot() {
        assertThatThrownBy(() -> repository().read("../outside.sql"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside configured root");
    }

    @Test
    void rejectsMissingFilesAndDirectories() throws Exception {
        Path directory = Files.createDirectory(root.resolve("queries"));

        assertThatThrownBy(() -> repository().read("missing.sql"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable SQL file");
        assertThatThrownBy(() -> repository().read(directory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable SQL file");
    }

    private SqlFileRepository repository() {
        return new SqlFileRepository(new RootPathPolicy(root));
    }
}
