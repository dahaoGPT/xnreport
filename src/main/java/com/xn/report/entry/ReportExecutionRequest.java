package com.xn.report.entry;

import com.xn.report.config.RootPathPolicy;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReportExecutionRequest {

    private final Path reportConfigPath;
    private final Path configRoot;
    private final Path sqlRoot;
    private final Path templateRoot;
    private final Path outputRoot;
    private final Path tempRoot;
    private final Map<String, Object> runtimeParameters;

    public ReportExecutionRequest(
            Path reportConfigPath,
            Path configRoot,
            Path sqlRoot,
            Path templateRoot,
            Path outputRoot,
            Path tempRoot,
            Map<String, Object> runtimeParameters) {
        this.configRoot = normalize(configRoot, "configRoot");
        this.sqlRoot = normalize(sqlRoot, "sqlRoot");
        this.templateRoot = normalize(templateRoot, "templateRoot");
        this.outputRoot = normalize(outputRoot, "outputRoot");
        this.tempRoot = normalize(tempRoot, "tempRoot");
        this.reportConfigPath =
                normalize(reportConfigPath, "reportConfigPath");
        assertUnder(this.configRoot, this.reportConfigPath, "reportConfigPath");
        this.runtimeParameters = freezeMap(runtimeParameters);
    }

    public Path getReportConfigPath() {
        return reportConfigPath;
    }

    public Path getConfigRoot() {
        return configRoot;
    }

    public Path getSqlRoot() {
        return sqlRoot;
    }

    public Path getTemplateRoot() {
        return templateRoot;
    }

    public Path getOutputRoot() {
        return outputRoot;
    }

    public Path getTempRoot() {
        return tempRoot;
    }

    public Map<String, Object> getRuntimeParameters() {
        return runtimeParameters;
    }

    private static Path normalize(Path value, String name) {
        return Objects.requireNonNull(value, name)
                .toAbsolutePath().normalize();
    }

    private static void assertUnder(Path root, Path value, String name) {
        Path relative;
        try {
            relative = root.relativize(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    name + " must use the same file system as configRoot",
                    exception);
        }
        new RootPathPolicy(root).resolve(relative);
    }

    private static Map<String, Object> freezeMap(Map<String, ?> values) {
        if (values == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "runtime parameter name"),
                    freeze(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(freeze(entry.getKey()), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?>) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (Iterable<?>) value) {
                copy.add(freeze(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> copy = new ArrayList<Object>();
            for (int index = 0; index < Array.getLength(value); index++) {
                copy.add(freeze(Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
