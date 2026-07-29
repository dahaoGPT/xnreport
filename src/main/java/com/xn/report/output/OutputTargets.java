package com.xn.report.output;

import java.nio.file.Path;
import java.util.Objects;

public final class OutputTargets {

    private final Path excel;
    private final Path word;

    public OutputTargets(Path excel, Path word) {
        this.excel = Objects.requireNonNull(excel, "excel");
        this.word = Objects.requireNonNull(word, "word");
    }

    public Path getExcel() {
        return excel;
    }

    public Path getWord() {
        return word;
    }
}
