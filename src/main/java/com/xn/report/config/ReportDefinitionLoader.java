package com.xn.report.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 报表配置文件加载与反序列化器。
 * <p>
 * 支持根据文件扩展名（{@code .yml}、{@code .yaml}、{@code .json}）分别使用 YAML 或 JSON ObjectMapper
 * 解析并反序列化生成强类型的 {@link ReportDefinition} 对象。
 * 开启了 {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} 特性，严格拦截未知属性，防止拼写错误隐蔽失效。
 * </p>
 */
public final class ReportDefinitionLoader {

    /** YAML 反序列化 Mapper。 */
    private final ObjectMapper yamlMapper;

    /** JSON 反序列化 Mapper。 */
    private final ObjectMapper jsonMapper;

    /**
     * 私有构造函数。
     */
    private ReportDefinitionLoader(ObjectMapper yamlMapper, ObjectMapper jsonMapper) {
        this.yamlMapper = yamlMapper;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 创建默认配置加载器实例。
     *
     * @return 配置加载器
     */
    public static ReportDefinitionLoader createDefault() {
        ObjectMapper yaml = configured(new ObjectMapper(new YAMLFactory()));
        ObjectMapper json = configured(new ObjectMapper());
        return new ReportDefinitionLoader(yaml, json);
    }

    /**
     * 开启未知属性校验等安全特性。
     */
    private static ObjectMapper configured(ObjectMapper mapper) {
        return mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * 从指定文件路径读取并解析报表定义。
     *
     * @param path 配置文件绝对或相对路径
     * @return 反序列化完成的 ReportDefinition 实例
     * @throws IllegalArgumentException 如果文件不存在、无法读取或内容格式非法
     */
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
