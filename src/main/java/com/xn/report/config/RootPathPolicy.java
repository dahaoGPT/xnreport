package com.xn.report.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 根路径安全边界校验策略。
 * <p>
 * 用于对用户配置的文件路径（如 SQL 文件路径、模板文件路径等）执行严格的沙箱边界校验，
 * 确保解析后的绝对路径与真实物理路径均在指定的 root 根目录边界内，防止软链接逃逸与路径穿越漏洞。
 * </p>
 */
public final class RootPathPolicy {

    /** 允许的根目录绝对规范化路径。 */
    private final Path root;

    /** 真实文件系统上的物理根目录边界（已解析软链接）。 */
    private final Path realRootBoundary;

    /**
     * 构造根路径安全策略。
     *
     * @param root 配置的受信任根目录，不可为 null
     */
    public RootPathPolicy(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.realRootBoundary = projectOntoRealFileSystem(
                this.root, "configured root");
    }

    /**
     * 解析相对路径字符串并校验其安全性。
     *
     * @param path 相对或绝对路径字符串
     * @return 安全的绝对规范化路径
     * @throws IllegalArgumentException 如果路径为空或逃逸出根目录
     */
    public Path resolve(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return resolve(Paths.get(path));
    }

    /**
     * 解析 Path 并校验其安全性。
     *
     * @param path 相对或绝对 Path 对象
     * @return 安全的绝对规范化路径
     * @throws IllegalArgumentException 如果路径逃逸出根目录
     */
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

    /**
     * 将路径投影到真实文件系统（逐级寻找真实存在的祖先节点并解析真实符号链接）。
     */
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
