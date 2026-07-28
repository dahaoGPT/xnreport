package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class RootPathPolicyTest {

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
}
