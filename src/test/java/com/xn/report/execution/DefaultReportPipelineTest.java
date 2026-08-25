package com.xn.report.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.analysis.AnalysisService;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportMetadata;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.entry.ExecutionStatus;
import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportExecutionResult;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.excel.ExcelGenerator;
import com.xn.report.output.CollisionPolicy;
import com.xn.report.output.OutputTargets;
import com.xn.report.output.PublishedOutputs;
import com.xn.report.word.WordGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;

class DefaultReportPipelineTest {

    Path directory;

    private ReportConfigLoader loader;
    private ReportConfigValidator validator;
    private DatasetQueryService queryService;
    private AnalysisService analysisService;
    private ExcelGenerator excelGenerator;
    private WordGenerator wordGenerator;
    private GeneratedOutputValidator outputValidator;
    private ReportOutputPublisher publisher;
    private DefaultReportPipeline pipeline;
    private ReportDefinition definition;
    private DatasetContext queried;
    private AnalysisContext analyzed;
    private ReportExecutionRequest request;
    private Path temporaryExcel;
    private Path temporaryWord;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory(
                java.nio.file.Paths.get("target"), "pipeline-");
        loader = mock(ReportConfigLoader.class);
        validator = mock(ReportConfigValidator.class);
        queryService = mock(DatasetQueryService.class);
        analysisService = mock(AnalysisService.class);
        excelGenerator = mock(ExcelGenerator.class);
        wordGenerator = mock(WordGenerator.class);
        outputValidator = mock(GeneratedOutputValidator.class);
        publisher = mock(ReportOutputPublisher.class);
        pipeline = new DefaultReportPipeline(
                loader,
                validator,
                queryService,
                analysisService,
                excelGenerator,
                wordGenerator,
                outputValidator,
                publisher);

        definition = new ReportDefinition();
        ReportMetadata metadata = new ReportMetadata();
        metadata.setCode("efficiency");
        metadata.setName("研发效能报告");
        metadata.setExcelFileName("efficiency.xlsx");
        metadata.setWordFileName("efficiency.docx");
        metadata.setCollisionPolicy("VERSIONED");
        definition.setReport(metadata);
        queried = DatasetContext.builder().build();
        analyzed = AnalysisContext.empty(queried);
        request = new ReportExecutionRequest(
                directory.resolve("config/report.yml"),
                directory.resolve("config"),
                directory.resolve("config/sql"),
                directory.resolve("templates"),
                directory.resolve("output"),
                directory.resolve("temp"),
                Collections.<String, Object>singletonMap("period", "2026-06"));
        temporaryExcel = directory.resolve("generated.xlsx");
        temporaryWord = directory.resolve("generated.docx");
        Files.write(temporaryExcel, new byte[] {1});
        Files.write(temporaryWord, new byte[] {1});

        when(loader.load(request.getReportConfigPath())).thenReturn(definition);
        when(queryService.executeAll(definition, request.getRuntimeParameters()))
                .thenReturn(queried);
        when(analysisService.analyze(
                eq(definition),
                eq(queried),
                eq(request.getRuntimeParameters()),
                any(Path.class)))
                .thenReturn(analyzed);
        when(excelGenerator.generate(
                any(ReportDefinition.class),
                any(AnalysisContext.class),
                any(ExecutionContext.class)))
                .thenReturn(temporaryExcel);
        when(wordGenerator.generate(
                any(ReportDefinition.class),
                any(AnalysisContext.class),
                any(ExecutionContext.class)))
                .thenReturn(temporaryWord);
        when(publisher.publish(
                any(Path.class),
                any(Path.class),
                any(OutputTargets.class),
                any(Path.class),
                any(CollisionPolicy.class)))
                .thenReturn(new PublishedOutputs(
                        directory.resolve("output/efficiency.xlsx"),
                        directory.resolve("output/efficiency.docx")));
    }

    @Test
    void queriesOnceThenGeneratesExcelBeforeWordValidatesAndPublishesBoth() {
        ReportExecutionResult result = pipeline.execute(request);

        InOrder order = inOrder(
                loader, validator, queryService, analysisService,
                excelGenerator, wordGenerator, outputValidator, publisher);
        order.verify(loader).load(request.getReportConfigPath());
        order.verify(validator).validateOrThrow(definition);
        order.verify(queryService).executeAll(
                definition, request.getRuntimeParameters());
        order.verify(analysisService).analyze(
                eq(definition),
                eq(queried),
                eq(request.getRuntimeParameters()),
                any(Path.class));
        order.verify(excelGenerator).generate(
                any(ReportDefinition.class),
                any(AnalysisContext.class),
                any(ExecutionContext.class));
        order.verify(wordGenerator).generate(
                any(ReportDefinition.class),
                any(AnalysisContext.class),
                any(ExecutionContext.class));
        order.verify(outputValidator).validate(temporaryExcel, temporaryWord);
        order.verify(publisher).publish(
                eq(temporaryExcel),
                eq(temporaryWord),
                any(OutputTargets.class),
                any(Path.class),
                any(CollisionPolicy.class));
        verifyNoMoreInteractions(queryService);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.getExcelPath().getFileName().toString())
                .isEqualTo("efficiency.xlsx");
        assertThat(result.getWordPath().getFileName().toString())
                .isEqualTo("efficiency.docx");
        assertThat(result.getMetrics().getDurationMillis(ExecutionStage.QUERY))
                .isGreaterThanOrEqualTo(0L);
    }

    @Test
    void returnsFailedWithOriginalCauseAndDoesNotPublishWhenWordFails() {
        IllegalStateException original = new IllegalStateException("broken image");
        when(wordGenerator.generate(
                any(ReportDefinition.class),
                any(AnalysisContext.class),
                any(ExecutionContext.class)))
                .thenThrow(new ReportException(
                        ReportErrorCode.DOCX_001, "word failed", original));

        ReportExecutionResult result = pipeline.execute(request);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getError().getErrorCode())
                .isEqualTo(ReportErrorCode.DOCX_001);
        assertThat(result.getFailure()).isInstanceOf(ReportException.class);
        assertThat(result.getFailure().getCause()).isSameAs(original);
        assertThat(result.getExcelPath()).isNull();
        assertThat(result.getWordPath()).isNull();
        assertThat(result.getFailedStage()).isEqualTo(ExecutionStage.GENERATE_WORD);
        verifyNoInteractions(outputValidator, publisher);
        assertWorkspaceWasCleaned();
    }

    @Test
    void outputValidationFailureNeverPublishes() {
        ReportException failure = new ReportException(
                ReportErrorCode.XLSX_002, "invalid workbook");
        org.mockito.Mockito.doThrow(failure)
                .when(outputValidator).validate(temporaryExcel, temporaryWord);

        ReportExecutionResult result = pipeline.execute(request);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getFailedStage())
                .isEqualTo(ExecutionStage.VALIDATE_OUTPUTS);
        assertThat(result.getFailure()).isSameAs(failure);
        verifyNoInteractions(publisher);
    }

    @Test
    void configurationFailureStopsBeforeQuery() {
        ReportException failure = new ReportException(
                ReportErrorCode.CFG_002, "invalid definition");
        org.mockito.Mockito.doThrow(failure)
                .when(validator).validateOrThrow(definition);

        ReportExecutionResult result = pipeline.execute(request);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getFailedStage())
                .isEqualTo(ExecutionStage.VALIDATE_CONFIG);
        verifyNoInteractions(queryService, analysisService, excelGenerator,
                wordGenerator, outputValidator, publisher);
    }

    @Test
    void invalidResolvedOutputNamesStopBeforeQuery() {
        definition.getReport().setExcelFileName("data.xlsx");
        definition.getReport().setWordFileName("report.docx");

        ReportExecutionResult result = pipeline.execute(request);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getFailedStage())
                .isEqualTo(ExecutionStage.VALIDATE_CONFIG);
        assertThat(result.getError().getErrorCode())
                .isEqualTo(ReportErrorCode.OUT_001);
        assertThat(result.getFailure().getMessage())
                .contains("same base name");
        verifyNoInteractions(queryService, analysisService, excelGenerator,
                wordGenerator, outputValidator, publisher);
    }

    @Test
    void unresolvedOutputNamePlaceholderStopsBeforeQuery() {
        definition.getReport().setExcelFileName(
                "efficiency-${unknown}.xlsx");
        definition.getReport().setWordFileName(
                "efficiency-${unknown}.docx");

        ReportExecutionResult result = pipeline.execute(request);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getFailedStage())
                .isEqualTo(ExecutionStage.VALIDATE_CONFIG);
        assertThat(result.getFailure().getMessage())
                .contains("unresolved output name placeholder");
        verifyNoInteractions(queryService, analysisService, excelGenerator,
                wordGenerator, outputValidator, publisher);
    }

    @Test
    void policyAndPublicationWarningsProduceSuccessWithWarnings() {
        analyzed = AnalysisContext.empty(queried)
                .withWarning("SKIP", "dataset", "empty",
                        "empty dataset was skipped");
        when(analysisService.analyze(
                eq(definition),
                eq(queried),
                eq(request.getRuntimeParameters()),
                any(Path.class)))
                .thenReturn(analyzed);
        when(publisher.publish(
                any(Path.class),
                any(Path.class),
                any(OutputTargets.class),
                any(Path.class),
                any(CollisionPolicy.class)))
                .thenReturn(new PublishedOutputs(
                        directory.resolve("output/efficiency.xlsx"),
                        directory.resolve("output/efficiency.docx"),
                        Arrays.asList("publication lock cleanup failed"),
                        Collections.<Path>emptyList()));

        ReportExecutionResult result = pipeline.execute(request);

        assertThat(result.getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS_WITH_WARNINGS);
        assertThat(result.getWarnings()).extracting("message")
                .containsExactly(
                        "empty dataset was skipped",
                        "publication lock cleanup failed");
    }

    @Test
    void requestDefensivelyCopiesRuntimeValuesAndNormalizesRoots() {
        java.util.List<String> centers =
                new java.util.ArrayList<String>(Arrays.asList("一中心"));
        java.util.Map<String, Object> values =
                new java.util.LinkedHashMap<String, Object>();
        values.put("centers", centers);
        ReportExecutionRequest copied = new ReportExecutionRequest(
                directory.resolve("config/../config/report.yml"),
                directory.resolve("config"),
                directory.resolve("config/sql"),
                directory.resolve("templates"),
                directory.resolve("output"),
                directory.resolve("temp"),
                values);

        centers.add("二中心");
        values.put("late", "value");

        assertThat(copied.getRuntimeParameters()).doesNotContainKey("late");
        assertThat(copied.getRuntimeParameters().get("centers"))
                .isEqualTo(Collections.singletonList("一中心"));
        assertThat(copied.getReportConfigPath())
                .isEqualTo(directory.resolve("config/report.yml")
                        .toAbsolutePath().normalize());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> copied.getRuntimeParameters().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restoresCallingThreadMdcValuesAfterExecution() {
        MDC.put("reportExecutionId", "caller-execution");
        MDC.put("reportStage", "caller-stage");
        MDC.put("unrelated", "keep-me");
        try {
            pipeline.execute(request);

            assertThat(MDC.get("reportExecutionId"))
                    .isEqualTo("caller-execution");
            assertThat(MDC.get("reportStage")).isEqualTo("caller-stage");
            assertThat(MDC.get("unrelated")).isEqualTo("keep-me");
            assertThat(MDC.get("reportCode")).isNull();
        } finally {
            MDC.clear();
        }
    }

    private void assertWorkspaceWasCleaned() {
        Path tempRoot = request.getTempRoot();
        assertThat(tempRoot).satisfies(path -> {
            if (Files.exists(path)) {
                try (java.util.stream.Stream<Path> paths = Files.list(path)) {
                    assertThat(paths).isEmpty();
                }
            }
        });
    }
}
