package com.xn.report.output;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PublishedOutputs {

    private final Path excel;
    private final Path word;
    private final List<String> warnings;
    private final List<Path> cleanupArtifactPaths;

    public PublishedOutputs(Path excel, Path word) {
        this(excel, word, Collections.<String>emptyList(), Collections.<Path>emptyList());
    }

    public PublishedOutputs(
            Path excel,
            Path word,
            List<String> warnings,
            List<Path> cleanupArtifactPaths) {
        this.excel = Objects.requireNonNull(excel, "excel");
        this.word = Objects.requireNonNull(word, "word");
        this.warnings = immutableCopy(warnings, "warnings");
        this.cleanupArtifactPaths =
                immutableCopy(cleanupArtifactPaths, "cleanupArtifactPaths");
    }

    public Path getExcel() {
        return excel;
    }

    public Path getWord() {
        return word;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<Path> getCleanupArtifactPaths() {
        return cleanupArtifactPaths;
    }

    PublishedOutputs withWarning(String warning) {
        List<String> combined = new ArrayList<String>(warnings);
        combined.add(Objects.requireNonNull(warning, "warning"));
        return new PublishedOutputs(excel, word, combined, cleanupArtifactPaths);
    }

    private static <T> List<T> immutableCopy(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
