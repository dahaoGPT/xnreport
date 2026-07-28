package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RootPathPolicyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsNormalizedPathOutsideConfiguredRoot() {
        Path root = Paths.get("target", "configured-root").toAbsolutePath();
        RootPathPolicy policy = new RootPathPolicy(root);

        assertThatThrownBy(() -> policy.resolve("nested/../../outside.sql"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside configured root");
    }

    @Test
    void allowsAndNormalizesPathInsideConfiguredRoot() {
        Path root = Paths.get("target", "configured-root").toAbsolutePath().normalize();
        RootPathPolicy policy = new RootPathPolicy(root);

        Path resolved = policy.resolve("nested/../query.sql");

        assertThat(resolved).isEqualTo(root.resolve("query.sql"));
        assertThat(resolved.isAbsolute()).isTrue();
    }

    @Test
    void rejectsExistingFileReachedThroughSymbolicLinkOutsideRoot() throws IOException {
        Path root = Files.createDirectory(tempDirectory.resolve("root"));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        Path outsideFile = Files.write(
                outside.resolve("secret.sql"),
                Arrays.asList("select 1"),
                StandardCharsets.UTF_8);
        createSymbolicLinkOrSkip(root.resolve("escape"), outside);
        RootPathPolicy policy = new RootPathPolicy(root);

        assertThatThrownBy(() -> policy.resolve("escape/" + outsideFile.getFileName()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside configured root");
    }

    @Test
    void rejectsNonexistentPathBelowSymbolicLinkOutsideRoot() throws IOException {
        Path root = Files.createDirectory(tempDirectory.resolve("root"));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        createSymbolicLinkOrSkip(root.resolve("escape"), outside);
        RootPathPolicy policy = new RootPathPolicy(root);

        assertThatThrownBy(() -> policy.resolve("escape/new/report.sql"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside configured root");
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
            return;
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            if (isWindows() && createWindowsJunction(link, target)) {
                return;
            }
            Assumptions.assumeTrue(false, "Filesystem links are unavailable: "
                    + exception.getMessage());
        }
    }

    private static boolean createWindowsJunction(Path link, Path target) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "cmd", "/c", "mklink", "/J",
                    link.toString(), target.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0 && Files.isDirectory(link);
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
