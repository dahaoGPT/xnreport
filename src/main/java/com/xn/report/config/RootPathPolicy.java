package com.xn.report.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class RootPathPolicy {

    private final Path root;

    public RootPathPolicy(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path resolve(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return resolve(Paths.get(path));
    }

    public Path resolve(Path path) {
        Path candidate = root.resolve(Objects.requireNonNull(path, "path"))
                .toAbsolutePath()
                .normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Path is outside configured root " + root + ": " + path);
        }
        return candidate;
    }
}
