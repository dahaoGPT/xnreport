package com.xn.report.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.analysis.AnalysisService;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportMetadata;
import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetRow;
import com.xn.report.entry.ExecutionStatus;
import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportExecutionResult;
import com.xn.report.excel.ExcelGenerator;
import com.xn.report.execution.DefaultReportPipeline;
import com.xn.report.execution.ExecutionContext;
import com.xn.report.output.PublishedOutputs;
import com.xn.report.word.WordGenerator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FiftyDatasetExecutionIT {

    @TempDir
    Path temporary;

    @Test
    void executesEachOfFiftyDatasetsExactlyOnce() {
        CountingQueryService query = new CountingQueryService(-1);
        AtomicBoolean published = new AtomicBoolean();

        ReportExecutionResult result = pipeline(query, published)
                .execute(request("success"));

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(query.executeAllCalls).isEqualTo(1);
        assertThat(query.counts).hasSize(50).allSatisfy(
                (id, count) -> assertThat(count).isEqualTo(1));
        assertThat(result.getDatasetRowCounts()).hasSize(50);
        assertThat(published).isTrue();
        assertThat(children(request("success").getTempRoot())).isEmpty();
    }

    @Test
    void failureOnFiftiethDatasetLeavesNoOutputsOrExecutionWorkspace() {
        CountingQueryService query = new CountingQueryService(50);
        AtomicBoolean published = new AtomicBoolean();
        ReportExecutionRequest request = request("failure");

        ReportExecutionResult result =
                pipeline(query, published).execute(request);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(query.executeAllCalls).isEqualTo(1);
        assertThat(query.counts).hasSize(50).allSatisfy(
                (id, count) -> assertThat(count).isEqualTo(1));
        assertThat(published).isFalse();
        assertThat(children(request.getOutputRoot())).isEmpty();
        assertThat(children(request.getTempRoot())).isEmpty();
    }

    private DefaultReportPipeline pipeline(
            DatasetQueryService queryService, AtomicBoolean published) {
        ReportDefinition definition = fiftyDatasetDefinition();
        AnalysisService analysis = new AnalysisService() {
            @Override
            public AnalysisContext analyze(
                    ReportDefinition ignored,
                    DatasetContext snapshot,
                    Map<String, Object> runtime,
                    Path chartDirectory) {
                return AnalysisContext.empty(snapshot);
            }
        };
        ExcelGenerator excel = new ExcelGenerator() {
            @Override
            public Path generate(
                    ReportDefinition ignored,
                    AnalysisContext context,
                    ExecutionContext execution) {
                return write(execution.getWorkspace().getExcelDirectory()
                        .resolve("report.xlsx"), "excel");
            }
        };
        WordGenerator word = new WordGenerator() {
            @Override
            public Path generate(
                    ReportDefinition ignored,
                    AnalysisContext context,
                    ExecutionContext execution) {
                return write(execution.getWorkspace().getWordDirectory()
                        .resolve("report.docx"), "word");
            }
        };
        return new DefaultReportPipeline(
                path -> definition,
                ignored -> { },
                queryService,
                analysis,
                excel,
                word,
                (generatedExcel, generatedWord) -> { },
                (generatedExcel, generatedWord, targets, outputRoot, collision) -> {
                    published.set(true);
                    try {
                        Files.createDirectories(outputRoot);
                        Files.copy(generatedExcel, targets.getExcel());
                        Files.copy(generatedWord, targets.getWord());
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                    return new PublishedOutputs(
                            targets.getExcel(), targets.getWord());
                });
    }

    private ReportExecutionRequest request(String name) {
        Path root = temporary.resolve(name).toAbsolutePath();
        Path config = root.resolve("config");
        return new ReportExecutionRequest(
                config.resolve("report.yml"),
                config,
                config.resolve("sql"),
                root.resolve("templates"),
                root.resolve("output"),
                root.resolve("temp"),
                Collections.<String, Object>emptyMap());
    }

    private static ReportDefinition fiftyDatasetDefinition() {
        ReportDefinition definition = new ReportDefinition();
        definition.setSchemaVersion("1.0");
        ReportMetadata metadata = new ReportMetadata();
        metadata.setCode("fifty-datasets");
        metadata.setName("Fifty datasets");
        definition.setReport(metadata);
        List<DatasetDefinition> datasets = new ArrayList<DatasetDefinition>();
        for (int index = 1; index <= 50; index++) {
            DatasetDefinition dataset = new DatasetDefinition();
            dataset.setId(String.format(java.util.Locale.ROOT, "dataset%02d", index));
            dataset.setSheetName(String.format(java.util.Locale.ROOT, "D%02d", index));
            dataset.setSql("SELECT " + index + " AS value");
            datasets.add(dataset);
        }
        definition.setDatasets(datasets);
        return definition;
    }

    private static Path write(Path path, String value) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, value.getBytes(StandardCharsets.UTF_8));
            return path;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<Path> children(Path root) {
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            return stream.collect(Collectors.toList());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CountingQueryService
            implements DatasetQueryService {

        private final int failAt;
        private final Map<String, Integer> counts =
                new LinkedHashMap<String, Integer>();
        private int executeAllCalls;

        private CountingQueryService(int failAt) {
            this.failAt = failAt;
        }

        @Override
        public DatasetContext executeAll(
                ReportDefinition definition,
                Map<String, Object> runtimeParameters) {
            executeAllCalls++;
            DatasetContext.Builder result = DatasetContext.builder();
            int position = 0;
            for (DatasetDefinition dataset : definition.getDatasets()) {
                position++;
                Integer previous = counts.get(dataset.getId());
                counts.put(dataset.getId(), previous == null ? 1 : previous + 1);
                if (position == failAt) {
                    throw new IllegalStateException(
                            "simulated failure at dataset 50");
                }
                result.put(DatasetResult.list(
                        dataset.getId(),
                        Collections.singletonList(
                                DatasetRow.of("value", position))));
            }
            return result.build();
        }
    }
}
