package com.xn.report.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.support.TestFixtures;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class TextRendererTest {

    private final TextRenderer renderer = TextRenderer.createDefault();

    @Test
    void rendersCurrentSummaryRuntimeAndDatasetValues() {
        String template = "${personName}平均耗时${avgHours|number:0.00}小时，"
                + "标准${dataset.baseline.standardHours|number:0.00}小时，"
                + "周期${runtime.period}，命中${summary.matchedCount}人";

        assertThat(renderer.render(template, TestFixtures.textContext()))
                .isEqualTo("张三平均耗时12.50小时，标准10.00小时，周期2026H1，命中2人");
    }

    @Test
    void rejectsUnresolvedAmbiguousAndExecutablePlaceholders() {
        assertThatThrownBy(() -> renderer.render(
                "${missingField}", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class)
                .hasMessageContaining("missingField")
                .satisfies(error -> assertThat(
                        ((TextRenderException) error).getErrorCode())
                        .isEqualTo(ReportErrorCode.TEXT_001));
        assertThatThrownBy(() -> renderer.render(
                "${period}", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class)
                .hasMessageContaining("qualified");
        assertThatThrownBy(() -> renderer.render(
                "${T(java.lang.Runtime).getRuntime()}", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class);
        assertThatThrownBy(() -> renderer.render(
                "${personName + 'x'}", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class);
    }

    @Test
    void supportsExplicitUnresolvedPolicies() {
        assertThat(renderer.render(
                "x${missing}y",
                TestFixtures.textContext(),
                UnresolvedPlaceholderPolicy.KEEP))
                .isEqualTo("x${missing}y");
        assertThat(renderer.render(
                "x${missing}y",
                TestFixtures.textContext(),
                UnresolvedPlaceholderPolicy.EMPTY))
                .isEqualTo("xy");
    }

    @Test
    void formatsAllWhitelistedValuesDeterministically() {
        TextRenderContext context = TextRenderContext.builder()
                .currentRow(TestFixtures.row(
                        "number", "12.5",
                        "ratio", "0.125",
                        "date", LocalDate.of(2026, 7, 28),
                        "instant", Instant.parse("2026-07-28T01:02:03Z"),
                        "duration", Duration.ofMinutes(90),
                        "missing", null,
                        "items", Arrays.asList("A", "B")))
                .build();

        assertThat(renderer.render(
                "${number|number:0.00}|${ratio|percent:0.0}|"
                        + "${date|date:yyyy-MM-dd}|"
                        + "${instant|datetime:yyyy-MM-dd HH:mm:ss}|"
                        + "${duration|durationHours:0.00}|"
                        + "${missing|default:无}|${items|join:、}",
                context))
                .isEqualTo("12.50|12.5%|2026-07-28|2026-07-28 01:02:03|1.50|无|A、B");
    }

    @Test
    void parserRejectsUnknownFormatterAndMalformedGrammar() {
        assertThatThrownBy(() -> renderer.render(
                "${avgHours|script:run}", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class)
                .hasMessageContaining("script");
        assertThatThrownBy(() -> renderer.render(
                "${avgHours|number:0.0|join:,}", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class);
        assertThatThrownBy(() -> renderer.render(
                "prefix ${avgHours", TestFixtures.textContext()))
                .isInstanceOf(TextRenderException.class);
    }

    @Test
    void contextDeeplySnapshotsValuesAndRejectsCycles() {
        List<String> source = new ArrayList<String>();
        source.add("A");
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        runtime.put("items", source);
        TextRenderContext context = TextRenderContext.builder()
                .runtime(runtime)
                .build();
        source.add("B");

        assertThat(renderer.render(
                "${runtime.items|join:,}", context)).isEqualTo("A");

        List<Object> cycle = new ArrayList<Object>();
        cycle.add(cycle);
        runtime.put("cycle", cycle);
        assertThatThrownBy(() -> TextRenderContext.builder()
                .runtime(runtime)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cyclic");
    }

    @Test
    void rejectsAmbiguousUnqualifiedNamesAcrossScopes() {
        TextRenderContext context = TextRenderContext.builder()
                .currentRow(TestFixtures.row("period", "row"))
                .summary(TestFixtures.parameters("period", "summary"))
                .runtime(TestFixtures.parameters("period", "runtime"))
                .build();

        assertThatThrownBy(() -> renderer.render("${period}", context))
                .isInstanceOf(TextRenderException.class)
                .hasMessageContaining("Ambiguous")
                .hasMessageContaining("period");
        assertThat(renderer.render(
                "${runtime.period}/${summary.period}", context))
                .isEqualTo("runtime/summary");
    }

    @Test
    void textTemplateImplementationDoesNotUseReflection() throws Exception {
        java.nio.file.Path textRoot = Paths.get(
                "src/main/java/com/xn/report/text");
        try (java.util.stream.Stream<java.nio.file.Path> files =
                     Files.walk(textRoot)) {
            java.util.Iterator<java.nio.file.Path> iterator = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .iterator();
            while (iterator.hasNext()) {
                java.nio.file.Path file = iterator.next();
                String source = new String(
                        Files.readAllBytes(file), StandardCharsets.UTF_8);
                assertThat(source)
                        .as(file.toString())
                        .doesNotContain("java.lang.reflect")
                        .doesNotContain("Class.forName(")
                        .doesNotContain(".getDeclared");
            }
        }
    }

    @Test
    void resolvesScalarDatasetByItsActualSchemaFieldAndRejectsTypos() {
        TextRenderContext context = TextRenderContext.builder()
                .datasets(DatasetContext.builder()
                        .put(DatasetResult.scalar(
                                "baselineSummary",
                                java.util.Collections.singletonList(
                                        TestFixtures.row(
                                                "standardHours",
                                                new java.math.BigDecimal("10")))))
                        .build())
                .build();

        assertThat(renderer.render(
                "${dataset.baselineSummary.standardHours|number:0.00}",
                context)).isEqualTo("10.00");
        assertThatThrownBy(() -> renderer.render(
                "${dataset.baselineSummary.standardHour}", context))
                .isInstanceOf(TextRenderException.class)
                .hasMessageContaining("standardHour");
    }

    @Test
    void listDatasetFieldsDoNotCreateFalseUnqualifiedAmbiguity() {
        TextRenderContext context = TextRenderContext.builder()
                .currentRow(TestFixtures.row("period", "current"))
                .datasets(DatasetContext.builder()
                        .put(DatasetResult.list(
                                "monthly",
                                java.util.Collections.singletonList(
                                        TestFixtures.row("period", "list"))))
                        .build())
                .build();

        assertThat(renderer.render("${period}", context))
                .isEqualTo("current");
    }

    @Test
    void contextSnapshotsDatesCalendarsArraysCollectionsAndMaps() {
        Date date = new Date(0L);
        Calendar calendar = Calendar.getInstance(
                TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, 1);
        int[] numbers = new int[]{1, 2};
        List<String> items = new ArrayList<String>();
        items.add("A");
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("status", "before");
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        runtime.put("date", date);
        runtime.put("calendar", calendar);
        runtime.put("numbers", numbers);
        runtime.put("items", items);
        runtime.put("nested", nested);
        runtime.put("bigInteger", BigInteger.TEN);
        runtime.put("month", YearMonth.of(2026, 3));

        TextRenderContext context =
                TextRenderContext.builder().runtime(runtime).build();
        String calendarSnapshot =
                renderer.render("${runtime.calendar}", context);

        date.setTime(86400000L);
        calendar.add(Calendar.MONTH, 1);
        numbers[0] = 9;
        items.add("B");
        nested.put("status", "after");

        assertThat(renderer.render(
                "${runtime.date|datetime:yyyy-MM-dd HH:mm:ss}|"
                        + "${runtime.numbers|join:,}|"
                        + "${runtime.items|join:,}|"
                        + "${runtime.nested}|"
                        + "${runtime.bigInteger}|${runtime.month}",
                context))
                .isEqualTo(
                        "1970-01-01 00:00:00|1,2|A|"
                                + "{status=before}|10|2026-03");
        assertThat(renderer.render("${runtime.calendar}", context))
                .isEqualTo(calendarSnapshot);
    }

    @Test
    void contextRejectsMutableNumbersBuildersAndUnknownObjects() {
        assertThatThrownBy(() -> TextRenderContext.builder()
                .runtime(TestFixtures.parameters(
                        "value", new AtomicInteger(1)))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AtomicInteger");
        assertThatThrownBy(() -> TextRenderContext.builder()
                .runtime(TestFixtures.parameters(
                        "value", new StringBuilder("mutable")))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StringBuilder");
        assertThatThrownBy(() -> TextRenderContext.builder()
                .runtime(TestFixtures.parameters(
                        "value", new MutableValue()))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MutableValue");
    }

    private static final class MutableValue {
        private int value;
    }
}
