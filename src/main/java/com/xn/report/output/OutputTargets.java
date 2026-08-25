package com.xn.report.output;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 报表期望发布的 Excel 与 Word 目标路径对。
 * <p>
 * 不可变值对象，封装了一组对应的 {@code .xlsx} 与 {@code .docx} 输出文件目标绝对路径。
 * </p>
 */
public final class OutputTargets {

    /** 目标 Excel 文件路径。 */
    private final Path excel;

    /** 目标 Word 文件路径。 */
    private final Path word;

    /**
     * 构造目标路径对。
     *
     * @param excel Excel 目标路径，不可为 null
     * @param word Word 目标路径，不可为 null
     */
    public OutputTargets(Path excel, Path word) {
        this.excel = Objects.requireNonNull(excel, "excel");
        this.word = Objects.requireNonNull(word, "word");
    }

    /**
     * 获取 Excel 目标路径。
     *
     * @return Excel 目标路径
     */
    public Path getExcel() {
        return excel;
    }

    /**
     * 获取 Word 目标路径。
     *
     * @return Word 目标路径
     */
    public Path getWord() {
        return word;
    }
}
