package com.xn.report.sql;

import com.xn.report.config.RootPathPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class SqlFileRepository {

    private final RootPathPolicy rootPathPolicy;

    public SqlFileRepository(RootPathPolicy rootPathPolicy) {
        this.rootPathPolicy = Objects.requireNonNull(
                rootPathPolicy, "rootPathPolicy");
    }

    public String read(String path) {
        return readResolved(rootPathPolicy.resolve(path));
    }

    public String read(Path path) {
        return readResolved(rootPathPolicy.resolve(path));
    }

    private String readResolved(Path path) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Not a readable SQL file: " + path);
        }
        try {
            String sql = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return sql.startsWith("\uFEFF") ? sql.substring(1) : sql;
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException(
                    "Cannot read SQL file " + path, exception);
        }
    }
}
