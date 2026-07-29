package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordOutputValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void reopensAndValidatesTocStylesOrderAndResolvedPlaceholders()
            throws Exception {
        Path output = tempDir.resolve("valid.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate()) {
            document.getParagraphs().stream()
                    .filter(p -> p.getText().contains("{{sections}}"))
                    .findFirst().get().getRuns().get(0).setText("正文", 0);
            document.createParagraph().setStyle("Heading1");
            document.getParagraphs().get(
                    document.getParagraphs().size() - 1)
                    .createRun().setText("交付速率");
            document.getSettings().setUpdateFields();
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
        }

        assertThatCode(() -> new WordOutputValidator().validate(
                output, 3, java.util.Collections.singletonList("交付速率"),
                0, 0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnresolvedPlaceholder() throws Exception {
        Path output = tempDir.resolve("invalid.docx");
        try (XWPFDocument document = WordTemplateLoaderTest.validTemplate();
             OutputStream stream = Files.newOutputStream(output)) {
            document.getSettings().setUpdateFields();
            document.write(stream);
        }

        assertThatThrownBy(() -> new WordOutputValidator().validate(
                output, 3, java.util.Collections.emptyList(), 0, 0))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("unresolved");
    }
}
