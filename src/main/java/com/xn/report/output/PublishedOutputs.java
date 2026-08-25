package com.xn.report.output;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 报表最终发布成功后的输出结果值对象。
 * <p>
 * 包含最终发布成功的 Excel 物理路径、Word 物理路径、发布过程产生的警告信息，
 * 以及发布后未能成功清理的中间临时文件列表（用于排查与补偿清理）。
 * 本对象为不可变对象。
 * </p>
 */
public final class PublishedOutputs {

    /** 最终已发布的 Excel 文件绝对路径。 */
    private final Path excel;

    /** 最终已发布的 Word 文件绝对路径。 */
    private final Path word;

    /** 发布过程中产生的警告列表（如清理失败提示）。 */
    private final List<String> warnings;

    /** 清理失败而残留的工件路径列表。 */
    private final List<Path> cleanupArtifactPaths;

    /**
     * 构造无警告的发布结果。
     *
     * @param excel 已发布的 Excel 路径
     * @param word 已发布的 Word 路径
     */
    public PublishedOutputs(Path excel, Path word) {
        this(excel, word, Collections.<String>emptyList(), Collections.<Path>emptyList());
    }

    /**
     * 构造带警告和残留工件的发布结果。
     *
     * @param excel 已发布的 Excel 路径
     * @param word 已发布的 Word 路径
     * @param warnings 警告信息列表
     * @param cleanupArtifactPaths 残留工件路径列表
     */
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

    /**
     * 追加单条警告信息并返回新的不可变结果对象。
     *
     * @param warning 警告信息
     * @return 新的 PublishedOutputs 对象
     */
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
