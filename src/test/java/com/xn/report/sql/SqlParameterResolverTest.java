package com.xn.report.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.ParameterBindingDefinition;
import com.xn.report.config.definition.ParameterSource;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@SuppressWarnings("unchecked")
class SqlParameterResolverTest {

    private final SqlParameterResolver resolver = new SqlParameterResolver();

    @Test
    void resolvesOnlyDeclaredRuntimeConstantAndSingleDatasetBindings() {
        DatasetDefinition definition = definition(
                binding("startedAt", runtime("start")),
                binding("reportDate", constant(LocalDate.of(2026, 7, 28))),
                binding("threshold", constant(new BigDecimal("12.50"))),
                binding("owner", dataset("summary", "owner")));
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        runtime.put("start", LocalDateTime.of(2026, 7, 28, 9, 30));
        runtime.put("undeclared", "must not leak");
        DatasetContext datasets = DatasetContext.builder()
                .put(DatasetResult.single(
                        "summary",
                        Collections.singletonList(DatasetRow.of("owner", "研发中心"))))
                .build();

        ResolvedSqlParameters result = resolver.resolve(definition, runtime, datasets);

        assertThat(result.asMap())
                .containsOnlyKeys("startedAt", "reportDate", "threshold", "owner");
        assertThat(result.asMap().get("startedAt"))
                .isEqualTo(Timestamp.valueOf(LocalDateTime.of(2026, 7, 28, 9, 30)));
        assertThat(result.asMap().get("reportDate"))
                .isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 7, 28)));
        assertThat(result.asMap().get("threshold"))
                .isEqualTo(new BigDecimal("12.50"));
        assertThat(result.asMap().get("owner")).isEqualTo("研发中心");
    }

    @Test
    void copiesCollectionParametersAndExposesAnIndependentJdbcSource() {
        List<String> centers = new ArrayList<String>(Arrays.asList("A", "B"));
        DatasetDefinition definition = definition(
                binding("centers", runtime("centerNames")));
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        runtime.put("centerNames", centers);

        ResolvedSqlParameters result = resolver.resolve(
                definition, runtime, DatasetContext.builder().build());
        centers.add("C");

        assertThat(result.asMap().get("centers"))
                .isEqualTo(Arrays.asList("A", "B"));
        assertThatThrownBy(() ->
                ((List<Object>) result.asMap().get("centers")).add("C"))
                .isInstanceOf(UnsupportedOperationException.class);

        MapSqlParameterSource jdbc = result.toMapSqlParameterSource();
        jdbc.addValue("centers", Collections.singletonList("changed"));
        assertThat(result.asMap().get("centers"))
                .isEqualTo(Arrays.asList("A", "B"));
    }

    @Test
    void rejectsMissingRuntimeParameterEvenWhenAnotherUndeclaredValueExists() {
        DatasetDefinition definition = definition(
                binding("requiredName", runtime("name")));
        Map<String, Object> runtime = Collections.<String, Object>singletonMap(
                "other", "value");

        assertThatThrownBy(() -> resolver.resolve(
                definition, runtime, DatasetContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsEmptyCollections() {
        DatasetDefinition definition = definition(
                binding("centers", runtime("centerNames")));
        Map<String, Object> runtime = Collections.<String, Object>singletonMap(
                "centerNames", Collections.emptyList());

        assertThatThrownBy(() -> resolver.resolve(
                definition, runtime, DatasetContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("centers")
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsDatasetBindingsThatAreNotSingleOrHaveNoRow() {
        DatasetDefinition definition = definition(
                binding("owner", dataset("summary", "owner")));
        DatasetContext listContext = DatasetContext.builder()
                .put(DatasetResult.list(
                        "summary",
                        Collections.singletonList(DatasetRow.of("owner", "A"))))
                .build();
        DatasetContext emptySingleContext = DatasetContext.builder()
                .put(DatasetResult.single(
                        "summary", Collections.<DatasetRow>emptyList()))
                .build();

        assertThatThrownBy(() -> resolver.resolve(
                definition, Collections.<String, Object>emptyMap(), listContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SINGLE");
        assertThatThrownBy(() -> resolver.resolve(
                definition,
                Collections.<String, Object>emptyMap(),
                emptySingleContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row");
    }

    @Test
    void deeplyCopiesMapsArraysAndCalendarsOnInputAndRead() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(123456789L);
        int[] numbers = new int[] {1, 2};
        List<String> names = new ArrayList<String>(Arrays.asList("A", "B"));
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("calendar", calendar);
        nested.put("numbers", numbers);
        nested.put("names", names);
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("payload", nested);

        ResolvedSqlParameters resolved = new ResolvedSqlParameters(input);
        calendar.setTimeInMillis(999L);
        numbers[0] = 9;
        names.add("C");
        nested.put("late", "leak");

        Map<String, Object> firstPayload =
                (Map<String, Object>) resolved.asMap().get("payload");
        assertThat(((Calendar) firstPayload.get("calendar")).getTimeInMillis())
                .isEqualTo(123456789L);
        assertThat((int[]) firstPayload.get("numbers")).containsExactly(1, 2);
        assertThat(firstPayload.get("names")).isEqualTo(Arrays.asList("A", "B"));
        assertThat(firstPayload).doesNotContainKey("late");
        assertThatThrownBy(() -> firstPayload.put("late", "blocked"))
                .isInstanceOf(UnsupportedOperationException.class);

        ((Calendar) firstPayload.get("calendar")).setTimeInMillis(1L);
        ((int[]) firstPayload.get("numbers"))[0] = 7;
        Map<String, Object> secondPayload =
                (Map<String, Object>) resolved.asMap().get("payload");
        assertThat(((Calendar) secondPayload.get("calendar")).getTimeInMillis())
                .isEqualTo(123456789L);
        assertThat((int[]) secondPayload.get("numbers")).containsExactly(1, 2);
    }

    @Test
    void rejectsUnsupportedMutableAndCyclicParameterValues() {
        Map<String, Object> unsupported = Collections.<String, Object>singletonMap(
                "builder", new StringBuilder("mutable"));
        List<Object> cycle = new ArrayList<Object>();
        cycle.add(cycle);

        assertThatThrownBy(() -> new ResolvedSqlParameters(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported mutable");
        assertThatThrownBy(() -> new ResolvedSqlParameters(
                Collections.<String, Object>singletonMap("cycle", cycle)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    void resolverRejectsSelfReferentialListWithoutStackOverflow() {
        List<Object> cycle = new ArrayList<Object>();
        cycle.add(cycle);
        DatasetDefinition definition = definition(
                binding("cycle", constant(cycle)));

        assertThatThrownBy(() -> resolver.resolve(
                definition,
                Collections.<String, Object>emptyMap(),
                DatasetContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    void resolverRejectsMutuallyReferentialListAndMapGraphs() {
        List<Object> left = new ArrayList<Object>();
        List<Object> right = new ArrayList<Object>();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        left.add(right);
        right.add(left);
        map.put("list", left);
        left.add(map);
        DatasetDefinition listDefinition = definition(
                binding("cycle", constant(left)));
        DatasetDefinition mapDefinition = definition(
                binding("cycle", constant(map)));

        assertThatThrownBy(() -> resolver.resolve(
                listDefinition,
                Collections.<String, Object>emptyMap(),
                DatasetContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
        assertThatThrownBy(() -> resolver.resolve(
                mapDefinition,
                Collections.<String, Object>emptyMap(),
                DatasetContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    private static DatasetDefinition definition(
            Map.Entry<String, ParameterBindingDefinition>... bindings) {
        DatasetDefinition definition = new DatasetDefinition();
        Map<String, ParameterBindingDefinition> parameters =
                new LinkedHashMap<String, ParameterBindingDefinition>();
        for (Map.Entry<String, ParameterBindingDefinition> binding : bindings) {
            parameters.put(binding.getKey(), binding.getValue());
        }
        definition.setParameters(parameters);
        return definition;
    }

    private static Map.Entry<String, ParameterBindingDefinition> binding(
            String name, ParameterBindingDefinition binding) {
        return new java.util.AbstractMap.SimpleImmutableEntry<
                String, ParameterBindingDefinition>(name, binding);
    }

    private static ParameterBindingDefinition runtime(String key) {
        ParameterBindingDefinition binding = new ParameterBindingDefinition();
        binding.setFrom(ParameterSource.RUNTIME);
        binding.setKey(key);
        return binding;
    }

    private static ParameterBindingDefinition constant(Object value) {
        ParameterBindingDefinition binding = new ParameterBindingDefinition();
        binding.setFrom(ParameterSource.CONSTANT);
        binding.setValue(value);
        return binding;
    }

    private static ParameterBindingDefinition dataset(String dataset, String field) {
        ParameterBindingDefinition binding = new ParameterBindingDefinition();
        binding.setFrom(ParameterSource.DATASET);
        binding.setDataset(dataset);
        binding.setField(field);
        return binding;
    }
}
