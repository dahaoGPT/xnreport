package com.xn.report.output;

import java.nio.file.Path;
import java.util.Objects;

public final class PublishedOutputs {

    private final Path excel;
    private final Path word;

    public PublishedOutputs(Path excel, Path word) {
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
