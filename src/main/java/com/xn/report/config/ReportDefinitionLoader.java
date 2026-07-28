package com.xn.report.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class ReportDefinitionLoader {

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    private ReportDefinitionLoader(ObjectMapper yamlMapper, ObjectMapper jsonMapper) {
        this.yamlMapper = yamlMapper;
        this.jsonMapper = jsonMapper;
    }

    public static ReportDefinitionLoader createDefault() {
        ObjectMapper yaml = configured(new ObjectMapper(new YAMLFactory()));
        ObjectMapper json = configured(new ObjectMapper());
        return new ReportDefinitionLoader(yaml, json);
    }

    private static ObjectMapper configured(ObjectMapper mapper) {
        return mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ReportDefinition load(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        ObjectMapper mapper = name.endsWith(".yml") || name.endsWith(".yaml")
                ? yamlMapper : jsonMapper;
        try {
            return mapper.readValue(path.toFile(), ReportDefinition.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Cannot load report definition " + path + ": " + ex.getMessage(), ex);
        }
    }
}
