package com.xn.report.execution;

import com.xn.report.analysis.AnalysisContext;
import com.xn.report.analysis.AnalysisService;
import com.xn.report.config.ReportDefinition;
import com.xn.report.config.ReportDefinitionLoader;
import com.xn.report.config.ReportDefinitionValidator;
import com.xn.report.config.ReportMetadata;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetQueryService;
import com.xn.report.dataset.DatasetQueryServiceFactory;
import com.xn.report.dataset.QueryOutcome;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.dataset.DatasetType;
import com.xn.report.entry.ExecutionStatus;
import com.xn.report.entry.ReportExecutionRequest;
import com.xn.report.entry.ReportExecutionResult;
import com.xn.report.entry.ReportWarning;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportErrorDetail;
import com.xn.report.error.ReportException;
import com.xn.report.excel.ExcelGenerator;
import com.xn.report.output.CollisionPolicy;
import com.xn.report.output.ExecutionWorkspace;
import com.xn.report.output.OutputNameRenderer;
import com.xn.report.output.OutputPublisher;
import com.xn.report.output.OutputTargets;
import com.xn.report.output.PublishedOutputs;
import com.xn.report.word.WordGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;

public final class DefaultReportPipeline implements ReportPipeline {

    private static final String MDC_EXECUTION_ID = "reportExecutionId";
    private static final String MDC_REPORT_CODE = "reportCode";
    private static final String MDC_STAGE = "reportStage";

    private final ReportConfigLoader loader;
    private final ReportConfigValidator validator;
    private final DatasetQueryServiceFactory queryServiceFactory;
    private final boolean queryWarningsSupported;
    private final AnalysisService analysisService;
    private final ExcelGenerator excelGenerator;
    private final WordGenerator wordGenerator;
    private final GeneratedOutputValidator outputValidator;
    private final ReportOutputPublisher publisher;
    private final OutputNameRenderer outputNameRenderer;

    public DefaultReportPipeline(
            ReportConfigLoader loader,
            ReportConfigValidator validator,
            DatasetQueryService queryService,
            AnalysisService analysisService,
            ExcelGenerator excelGenerator,
            WordGenerator wordGenerator,
            GeneratedOutputValidator outputValidator,
            ReportOutputPublisher publisher) {
        this(loader, validator, queryService, analysisService,
                excelGenerator, wordGenerator, outputValidator, publisher,
                new OutputNameRenderer());
    }

    DefaultReportPipeline(
            ReportConfigLoader loader,
            ReportConfigValidator validator,
            DatasetQueryService queryService,
            AnalysisService analysisService,
            ExcelGenerator excelGenerator,
            WordGenerator wordGenerator,
            GeneratedOutputValidator outputValidator,
            ReportOutputPublisher publisher,
            OutputNameRenderer outputNameRenderer) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.validator = Objects.requireNonNull(validator, "validator");
        final DatasetQueryService fixed = Objects.requireNonNull(
                queryService, "queryService");
        this.queryServiceFactory = sqlRoot -> fixed;
        this.queryWarningsSupported = false;
        this.analysisService =
                Objects.requireNonNull(analysisService, "analysisService");
        this.excelGenerator =
                Objects.requireNonNull(excelGenerator, "excelGenerator");
        this.wordGenerator =
                Objects.requireNonNull(wordGenerator, "wordGenerator");
        this.outputValidator =
                Objects.requireNonNull(outputValidator, "outputValidator");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.outputNameRenderer =
                Objects.requireNonNull(outputNameRenderer, "outputNameRenderer");
    }

    public static DefaultReportPipeline createDefault(
            DatasetQueryService queryService) {
        ReportDefinitionLoader definitionLoader =
                ReportDefinitionLoader.createDefault();
        ReportDefinitionValidator definitionValidator =
                new ReportDefinitionValidator();
        return new DefaultReportPipeline(
                definitionLoader::load,
                definition -> definitionValidator.validate(definition)
                        .throwIfInvalid(),
                queryService,
                new AnalysisService(),
                new ExcelGenerator(),
                new WordGenerator(),
                DefaultReportPipeline::validateGeneratedFiles,
                (excel, word, targets, root, collision) ->
                        new OutputPublisher(root, collision)
                                .publish(excel, word, targets));
    }

    public static DefaultReportPipeline createDefault(
            DatasetQueryServiceFactory queryServiceFactory) {
        ReportDefinitionLoader definitionLoader =
                ReportDefinitionLoader.createDefault();
        ReportDefinitionValidator definitionValidator =
                new ReportDefinitionValidator();
        return new DefaultReportPipeline(
                definitionLoader::load,
                definition -> definitionValidator.validate(definition)
                        .throwIfInvalid(),
                queryServiceFactory,
                new AnalysisService(),
                new ExcelGenerator(),
                new WordGenerator(),
                DefaultReportPipeline::validateGeneratedFiles,
                (excel, word, targets, root, collision) ->
                        new OutputPublisher(root, collision)
                                .publish(excel, word, targets));
    }

    private DefaultReportPipeline(
            ReportConfigLoader loader,
            ReportConfigValidator validator,
            DatasetQueryServiceFactory queryServiceFactory,
            AnalysisService analysisService,
            ExcelGenerator excelGenerator,
            WordGenerator wordGenerator,
            GeneratedOutputValidator outputValidator,
            ReportOutputPublisher publisher) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.queryServiceFactory = Objects.requireNonNull(
                queryServiceFactory, "queryServiceFactory");
        this.queryWarningsSupported = true;
        this.analysisService = Objects.requireNonNull(analysisService,
                "analysisService");
        this.excelGenerator = Objects.requireNonNull(excelGenerator,
                "excelGenerator");
        this.wordGenerator = Objects.requireNonNull(wordGenerator,
                "wordGenerator");
        this.outputValidator = Objects.requireNonNull(outputValidator,
                "outputValidator");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.outputNameRenderer = new OutputNameRenderer();
    }

    @Override
    public ReportExecutionResult execute(ReportExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        String executionId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        ExecutionMetrics.Mutable mutableMetrics =
                ExecutionMetrics.begin(startedAt);
        ExecutionWorkspace workspace = null;
        ExecutionContext context = null;
        PublishedOutputs published = null;
        Throwable failure = null;
        ExecutionStage failedStage = null;
        ReportDefinition definition = null;
        DatasetContext analyzedDatasets = null;
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();

        MDC.put(MDC_EXECUTION_ID, executionId);
        try {
            workspace = ExecutionWorkspace.create(request.getTempRoot());
            context = new ExecutionContext(
                    executionId, request, workspace, mutableMetrics);
            final ExecutionContext activeContext = context;

            definition = stage(activeContext, ExecutionStage.LOAD_CONFIG,
                    () -> loader.load(request.getReportConfigPath()));
            activeContext.setDefinition(definition);
            putReportCode(definition);

            final ReportDefinition configured = definition;
            stage(activeContext, ExecutionStage.VALIDATE_CONFIG, () -> {
                validator.validateOrThrow(configured);
                return null;
            });

            DatasetQueryService activeQueryService =
                    queryServiceFactory.create(request.getSqlRoot());
            QueryOutcome queryOutcome = stage(
                    activeContext,
                    ExecutionStage.QUERY,
                    () -> queryWarningsSupported
                            ? activeQueryService.executeAllWithWarnings(
                                    configured,
                                    request.getRuntimeParameters())
                            : new QueryOutcome(
                                    activeQueryService.executeAll(
                                            configured,
                                            request.getRuntimeParameters()),
                                    Collections.emptyList()));
            DatasetContext snapshot = queryOutcome.getDatasets();
            for (com.xn.report.policy.ReportWarning warning
                    : queryOutcome.getWarnings()) {
                activeContext.addWarning(ReportWarning.fromPolicy(warning));
            }
            activeContext.setQuerySnapshot(snapshot);

            AnalysisContext analysis = stage(
                    activeContext,
                    ExecutionStage.ANALYZE,
                    () -> analysisService.analyze(
                            configured,
                            snapshot,
                            request.getRuntimeParameters(),
                            activeContext.getWorkspace().getChartsDirectory()));
            activeContext.setAnalysisContext(analysis);
            analyzedDatasets = analysis.getDatasetContext();
            for (com.xn.report.policy.ReportWarning warning
                    : analysis.getWarnings()) {
                activeContext.addWarning(ReportWarning.fromPolicy(warning));
            }

            Path excel = stage(
                    activeContext,
                    ExecutionStage.GENERATE_EXCEL,
                    () -> excelGenerator.generate(
                            configured, analysis, activeContext));
            Path word = stage(
                    activeContext,
                    ExecutionStage.GENERATE_WORD,
                    () -> wordGenerator.generate(
                            configured, analysis, activeContext));

            stage(activeContext, ExecutionStage.VALIDATE_OUTPUTS, () -> {
                outputValidator.validate(excel, word);
                return null;
            });
            published = stage(
                    activeContext,
                    ExecutionStage.PUBLISH,
                    () -> {
                        OutputTargets targets =
                                outputTargets(configured, request, executionId);
                        CollisionPolicy collision = collisionPolicy(configured);
                        return publisher.publish(
                                excel, word, targets,
                                request.getOutputRoot(), collision);
                    });
            for (String warning : published.getWarnings()) {
                activeContext.addWarning(ReportWarning.publication(warning));
            }
            activeContext.setStage(ExecutionStage.COMPLETED);
        } catch (RuntimeException exception) {
            failure = exception;
            failedStage = context == null
                    ? ExecutionStage.INITIALIZE : context.getStage();
        } finally {
            if (context != null) {
                warnings.addAll(context.getWarnings());
            }
            if (workspace != null) {
                try {
                    workspace.close();
                } catch (RuntimeException cleanupFailure) {
                    if (published != null) {
                        warnings.add(ReportWarning.publication(
                                "execution workspace cleanup failed after publication: "
                                        + cleanupFailure.getMessage()));
                    } else if (failure == null) {
                        failure = cleanupFailure;
                        failedStage = context == null
                                ? ExecutionStage.INITIALIZE : context.getStage();
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            MDC.clear();
            if (previousMdc != null) {
                MDC.setContextMap(previousMdc);
            }
        }

        Instant finishedAt = Instant.now();
        ExecutionMetrics metrics = mutableMetrics.snapshot(finishedAt);
        String reportCode = reportCode(definition);
        if (failure != null) {
            return failed(
                    executionId, reportCode, startedAt, finishedAt,
                    analyzedDatasets, warnings, failure, failedStage, metrics);
        }
        ExecutionStatus status = warnings.isEmpty()
                ? ExecutionStatus.SUCCESS
                : ExecutionStatus.SUCCESS_WITH_WARNINGS;
        return new ReportExecutionResult(
                executionId,
                reportCode,
                status,
                startedAt,
                finishedAt,
                published.getExcel(),
                published.getWord(),
                rowCounts(analyzedDatasets),
                warnings,
                null,
                null,
                null,
                metrics);
    }

    private <T> T stage(
            ExecutionContext context,
            ExecutionStage stage,
            StageOperation<T> operation) {
        context.setStage(stage);
        MDC.put(MDC_STAGE, stage.name());
        long started = context.getMutableMetrics().start();
        try {
            return operation.run();
        } finally {
            context.getMutableMetrics().finish(stage, started);
        }
    }

    private OutputTargets outputTargets(
            ReportDefinition definition,
            ReportExecutionRequest request,
            String executionId) {
        ReportMetadata metadata = definition.getReport();
        Map<String, Object> values =
                new LinkedHashMap<String, Object>(
                        request.getRuntimeParameters());
        values.put("executionId", executionId);
        values.put("reportCode", metadata.getCode());
        String base = hasText(metadata.getCode())
                ? metadata.getCode() : "report";
        String excelTemplate = hasText(metadata.getExcelFileName())
                ? metadata.getExcelFileName() : base + ".xlsx";
        String wordTemplate = hasText(metadata.getWordFileName())
                ? metadata.getWordFileName() : base + ".docx";
        String excelName = outputNameRenderer.render(excelTemplate, values);
        String wordName = outputNameRenderer.render(wordTemplate, values);
        return new OutputTargets(
                request.getOutputRoot().resolve(excelName),
                request.getOutputRoot().resolve(wordName));
    }

    private static CollisionPolicy collisionPolicy(
            ReportDefinition definition) {
        String value = definition.getReport().getCollisionPolicy();
        if (!hasText(value)) {
            return CollisionPolicy.VERSIONED;
        }
        try {
            return CollisionPolicy.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ReportException(
                    ReportErrorCode.CFG_002,
                    "Unknown collision policy: " + value,
                    exception);
        }
    }

    private static void validateGeneratedFiles(Path excel, Path word) {
        validateGeneratedFile(excel, ".xlsx");
        validateGeneratedFile(word, ".docx");
    }

    private static void validateGeneratedFile(Path path, String extension) {
        if (path == null
                || !Files.isRegularFile(path)
                || !path.getFileName().toString()
                        .toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new ReportException(
                    ReportErrorCode.OUT_001,
                    "Generated output is missing or has wrong extension: " + path);
        }
    }

    private static ReportExecutionResult failed(
            String executionId,
            String reportCode,
            Instant startedAt,
            Instant finishedAt,
            DatasetContext datasets,
            List<ReportWarning> warnings,
            Throwable failure,
            ExecutionStage failedStage,
            ExecutionMetrics metrics) {
        ReportErrorCode code = failure instanceof ReportException
                ? ((ReportException) failure).getErrorCode()
                : fallbackCode(failedStage);
        ReportErrorDetail detail = new ReportErrorDetail(
                code,
                executionId,
                failedStage == null ? null : failedStage.name(),
                reportCode,
                failure instanceof ReportException
                        ? ((ReportException) failure).getComponentId()
                        : null,
                failure.getMessage() == null
                        ? failure.getClass().getSimpleName()
                        : failure.getMessage());
        return new ReportExecutionResult(
                executionId,
                reportCode,
                ExecutionStatus.FAILED,
                startedAt,
                finishedAt,
                null,
                null,
                rowCounts(datasets),
                warnings,
                detail,
                failure,
                failedStage,
                metrics);
    }

    private static ReportErrorCode fallbackCode(ExecutionStage stage) {
        if (stage == ExecutionStage.LOAD_CONFIG
                || stage == ExecutionStage.VALIDATE_CONFIG) {
            return ReportErrorCode.CFG_002;
        }
        if (stage == ExecutionStage.QUERY) {
            return ReportErrorCode.SQL_002;
        }
        if (stage == ExecutionStage.GENERATE_EXCEL) {
            return ReportErrorCode.XLSX_001;
        }
        if (stage == ExecutionStage.GENERATE_WORD) {
            return ReportErrorCode.DOCX_001;
        }
        return ReportErrorCode.OUT_003;
    }

    private static Map<String, Long> rowCounts(DatasetContext context) {
        if (context == null) {
            return Collections.emptyMap();
        }
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        for (String id : context.ids()) {
            DatasetResult result = context.get(id);
            long count;
            if (result.type() == DatasetType.LIST) {
                count = result.list().size();
            } else if (result.type() == DatasetType.SINGLE) {
                count = result.single() == null ? 0L : 1L;
            } else {
                count = result.scalar() == null ? 0L : 1L;
            }
            counts.put(id, Long.valueOf(count));
        }
        return counts;
    }

    private static void putReportCode(ReportDefinition definition) {
        String code = reportCode(definition);
        if (code != null) {
            MDC.put(MDC_REPORT_CODE, code);
        }
    }

    private static String reportCode(ReportDefinition definition) {
        return definition == null || definition.getReport() == null
                ? null : definition.getReport().getCode();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    private interface StageOperation<T> {
        T run();
    }
}
