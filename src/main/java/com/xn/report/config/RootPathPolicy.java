package com.xn.report.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class RootPathPolicy {

    private final Path root;
    private final Path realRootBoundary;

    public RootPathPolicy(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.realRootBoundary = projectOntoRealFileSystem(
                this.root, "configured root");
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
            throw outsideRoot(path);
        }
        Path realCandidate = projectOntoRealFileSystem(candidate, "path");
        if (!realCandidate.startsWith(realRootBoundary)) {
            throw outsideRoot(path);
        }
        return candidate;
    }

    private Path projectOntoRealFileSystem(Path path, String description) {
        Path existingAncestor = path;
        while (existingAncestor != null
                && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve " + description + " on the real file system: " + path);
        }
        try {
            Path realAncestor = existingAncestor.toRealPath();
            Path unresolvedSuffix = existingAncestor.relativize(path);
            return realAncestor.resolve(unresolvedSuffix).normalize();
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException(
                    "Cannot resolve " + description + " on the real file system: " + path,
                    exception);
        }
    }

    private IllegalArgumentException outsideRoot(Path path) {
        return new IllegalArgumentException(
                "Path is outside configured root " + root + ": " + path);
    }
}
