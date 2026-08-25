package com.xn.report.dataset;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.FieldDefinition;
import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.policy.EmptyDataPolicy;
import com.xn.report.policy.MissingFieldPolicy;
import com.xn.report.policy.NullValuePolicy;
import com.xn.report.policy.PolicyExecutionBridge;
import com.xn.report.policy.PolicyResolver;
import com.xn.report.policy.TypeMismatchPolicy;
import com.xn.report.sql.SqlQueryResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Iterator;

/**
 * 数据集执行结果契约校验与策略治理器。
 * <p>
 * 对 SQL 查询或内存 Transform 产出的原始数据进行全流程校验与治理：
 * <ul>
 *   <li><b>形态维度检查</b>：校验 SCALAR（1行1列）、SINGLE（最多1行）与 LIST 的行数与列数约束。</li>
 *   <li><b>空数据策略治理（{@link EmptyDataPolicy}）</b>：当查询结果为空时，按策略触发 FAIL、SKIP 或默认占位。</li>
 *   <li><b>缺失字段策略治理（{@link MissingFieldPolicy}）</b>：检查 expectedFields 契约，若缺失列根据策略执行 FAIL、USE_DEFAULT（注入默认值）或 WARN_AND_SKIP（跳过整行）。</li>
 *   <li><b>NULL 值策略治理（{@link NullValuePolicy}）</b>：对必填字段为 NULL 执行 FAIL 或填充预设默认值。</li>
 *   <li><b>类型不匹配治理（{@link TypeMismatchPolicy}）</b>：执行强类型检查与安全转换（SAFE_CONVERT），若无法转换则抛出 {@link ReportErrorCode#DATA_003}。</li>
 * </ul>
 * </p>
 */
public final class DatasetResultValidator {

    /** 支持的标准字段类型映射表。 */
    private static final Map<String, Class<?>> FIELD_TYPES = fieldTypes();

    /** 策略执行与告警下发桥接器。 */
    private final PolicyExecutionBridge policyBridge;

    /** 报表级别默认兜底策略。 */
    private final PolicyDefinition reportPolicies;

    /**
     * 使用默认系统策略构造校验器。
     */
    public DatasetResultValidator() {
        this(
                new PolicyExecutionBridge(new PolicyResolver(
                        PolicyDefinition.systemDefaults())),
                PolicyDefinition.systemDefaults());
    }

    /**
     * 使用指定策略桥接器与报表级策略构造校验器。
     *
     * @param policyBridge 策略执行桥接器
     * @param reportPolicies 报表级策略
     */
    public DatasetResultValidator(
            PolicyExecutionBridge policyBridge,
            PolicyDefinition reportPolicies) {
        this.policyBridge = Objects.requireNonNull(policyBridge, "policyBridge");
        this.reportPolicies = reportPolicies == null
                ? PolicyDefinition.systemDefaults() : reportPolicies;
    }

    /**
     * 校验内存生成的行数据列表。
     *
     * @param definition 数据集配置定义
     * @param sourceRows 内存数据行列表
     * @return 校验并治理后的 DatasetResult
     */
    public DatasetResult validate(
            DatasetDefinition definition, List<DatasetRow> sourceRows) {
        List<DatasetRow> rows = copyRows(
                definition == null ? "<unknown>" : definition.getId(), sourceRows);
        return validateInternal(
                definition,
                new SqlQueryResult(DatasetSchema.infer(rows), rows),
                false);
    }

    /**
     * 校验 SQL 执行结果。
     *
     * @param definition 数据集配置定义
     * @param queryResult SQL 查询结果
     * @return 校验并治理后的 DatasetResult
     */
    public DatasetResult validate(
            DatasetDefinition definition, SqlQueryResult queryResult) {
        return validateInternal(definition, queryResult, true);
    }

    /**
     * 内部通用校验与治理流水线。
     */
    private DatasetResult validateInternal(
            DatasetDefinition definition,
            SqlQueryResult queryResult,
            boolean validateMetadataTypes) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(queryResult, "queryResult");
        String datasetId = requireText(definition.getId(), "dataset id");
        DatasetType resultType = Objects.requireNonNull(
            definition.getResultType(),
                "resultType for dataset " + datasetId);
        List<DatasetRow> rows = copyRows(datasetId, queryResult.rows());
        DatasetSchema schema = queryResult.schema();

        // 1. 空数据策略处理
        if (rows.isEmpty()) {
            EmptyDataPolicy emptyData = policyBridge.onEmptyData(
                    null,
                    null,
                    definition.getPolicies(),
                    reportPolicies,
                    "dataset",
                    datasetId,
                    "dataset returned no rows");
            if (emptyData == EmptyDataPolicy.FAIL) {
                throw error(
                        ReportErrorCode.DATA_001,
                        datasetId,
                        "Dataset " + datasetId + " returned no rows");
            }
        }
        // 2. 形态与行数校验
        validateShape(datasetId, resultType, schema, rows);

        // 3. 字段 Schema 契约、缺失值与类型匹配治理
        ProcessedData processed = validateFields(
                datasetId,
                resultType,
                definition.getPolicies(),
                definition.getExpectedFields(),
                schema,
                rows,
                validateMetadataTypes);
        validateShape(
                datasetId, resultType, processed.schema, processed.rows);
        return buildResult(
                datasetId, resultType, processed.schema, processed.rows);
    }

    private static void validateShape(
            String datasetId,
            DatasetType resultType,
            DatasetSchema schema,
            List<DatasetRow> rows) {
        if ((resultType == DatasetType.SCALAR || resultType == DatasetType.SINGLE)
                && rows.size() > 1) {
            throw error(
                    ReportErrorCode.DATA_001,
                    datasetId,
                    "Dataset " + datasetId + " declared as " + resultType
                            + " returned " + rows.size() + " rows");
        }
        if (resultType == DatasetType.SCALAR
                && schema.fieldNames().size() != 1) {
            throw error(
                    ReportErrorCode.DATA_001,
                    datasetId,
                    "Scalar dataset " + datasetId
                            + " must return exactly one column");
        }
    }

    /**
     * 逐字段与逐行执行契约校验与降级补齐。
     */
    private ProcessedData validateFields(
            String datasetId,
            DatasetType resultType,
            PolicyDefinition datasetPolicies,
            Map<String, FieldDefinition> expectedFields,
            DatasetSchema schema,
            List<DatasetRow> rows,
            boolean validateMetadataTypes) {
        if (expectedFields == null || expectedFields.isEmpty()) {
            return new ProcessedData(schema, rows);
        }
        LinkedHashMap<String, Class<?>> outputSchema =
                new LinkedHashMap<String, Class<?>>(schema.asMap());
        List<LinkedHashMap<String, Object>> outputRows =
                mutableRows(rows);
        boolean replaceScalarAlias = resultType == DatasetType.SCALAR
                && expectedFields.size() == 1;
        for (Map.Entry<String, FieldDefinition> expected
                : expectedFields.entrySet()) {
            String fieldName = requireText(expected.getKey(), "expected field");
            FieldDefinition field = Objects.requireNonNull(
                    expected.getValue(),
                    "field definition for " + fieldName);
            Class<?> expectedType = resolveType(fieldName, field.getType());
            if (validateMetadataTypes && !schema.containsField(fieldName)) {
                MissingFieldPolicy policy = policyBridge.onMissingField(
                        null,
                        null,
                        datasetPolicies,
                        reportPolicies,
                        "dataset",
                        datasetId,
                        "missing metadata field " + fieldName);
                if (policy == MissingFieldPolicy.FAIL) {
                    throw error(
                            ReportErrorCode.DATA_002,
                            datasetId,
                            "Dataset " + datasetId
                                    + " is missing expected alias " + fieldName);
                }
                if (policy == MissingFieldPolicy.WARN_AND_SKIP) {
                    applyExpectedSchema(
                            outputSchema,
                            fieldName,
                            expectedType,
                            replaceScalarAlias);
                    return new ProcessedData(
                            schemaFrom(outputSchema),
                            Collections.<DatasetRow>emptyList());
                }
                Object defaultValue = typedDefault(
                        datasetId, fieldName, field, expectedType,
                        ReportErrorCode.DATA_002);
                applyExpectedSchema(
                        outputSchema,
                        fieldName,
                        expectedType,
                        replaceScalarAlias);
                for (Map<String, Object> row : outputRows) {
                    if (replaceScalarAlias) {
                        row.clear();
                    }
                    row.put(fieldName, defaultValue);
                }
            }
            Class<?> actualType = schema.containsField(fieldName)
                    ? schema.typeOf(fieldName) : Object.class;
            if (validateMetadataTypes
                    && schema.containsField(fieldName)
                    && !schemaMatches(expectedType, actualType)) {
                TypeMismatchPolicy policy = policyBridge.onTypeMismatch(
                        null,
                        null,
                        datasetPolicies,
                        reportPolicies,
                        "dataset",
                        datasetId,
                        "metadata type mismatch for " + fieldName);
                if (policy == TypeMismatchPolicy.FAIL
                        || (policy == TypeMismatchPolicy.SAFE_CONVERT
                        && !supportsConversion(actualType, expectedType))) {
                    throw typeError(
                            datasetId,
                            fieldName,
                            field.getType(),
                            actualType.getSimpleName());
                }
                if (policy == TypeMismatchPolicy.WARN_AND_SKIP) {
                    replaceSchemaField(
                            outputSchema, fieldName, expectedType);
                    return new ProcessedData(
                            schemaFrom(outputSchema),
                            Collections.<DatasetRow>emptyList());
                }
                replaceSchemaField(outputSchema, fieldName, expectedType);
            }
            int rowIndex = -1;
            for (Iterator<LinkedHashMap<String, Object>> iterator =
                    outputRows.iterator(); iterator.hasNext();) {
                rowIndex++;
                LinkedHashMap<String, Object> row = iterator.next();
                String actualField = findField(row, fieldName);
                if (actualField == null) {
                    MissingFieldPolicy policy = policyBridge.onMissingField(
                            null,
                            null,
                            datasetPolicies,
                            reportPolicies,
                            "dataset-row",
                            datasetId,
                            "missing field " + fieldName + " at row " + rowIndex);
                    if (policy == MissingFieldPolicy.WARN_AND_SKIP) {
                        iterator.remove();
                        continue;
                    }
                    if (policy == MissingFieldPolicy.USE_DEFAULT) {
                        if (replaceScalarAlias) {
                            row.clear();
                        }
                        row.put(
                                fieldName,
                                typedDefault(
                                        datasetId,
                                        fieldName,
                                        field,
                                        expectedType,
                                        ReportErrorCode.DATA_002));
                        actualField = fieldName;
                    } else {
                        throw error(
                                ReportErrorCode.DATA_002,
                                datasetId,
                                "Dataset " + datasetId
                                        + " is missing expected alias " + fieldName
                                        + " at row " + rowIndex);
                    }
                }
                Object value = row.get(actualField);
                if (value == null) {
                    NullValuePolicy policy = policyBridge.onNullValue(
                            null,
                            null,
                            datasetPolicies,
                            reportPolicies,
                            "dataset-row",
                            datasetId,
                            "null field " + fieldName + " at row " + rowIndex);
                    if (policy == NullValuePolicy.USE_DEFAULT) {
                        row.put(
                                actualField,
                                typedDefault(
                                        datasetId,
                                        fieldName,
                                        field,
                                        expectedType,
                                        ReportErrorCode.DATA_002));
                        continue;
                    }
                    if (policy == NullValuePolicy.FAIL || field.isRequired()) {
                        throw error(
                                ReportErrorCode.DATA_002,
                                datasetId,
                                "Required field " + fieldName + " is null in dataset "
                                        + datasetId + " at row " + rowIndex);
                    }
                    continue;
                }
                if (!matches(expectedType, value)) {
                    TypeMismatchPolicy policy = policyBridge.onTypeMismatch(
                            null,
                            null,
                            datasetPolicies,
                            reportPolicies,
                            "dataset-row",
                            datasetId,
                            "type mismatch for " + fieldName
                                    + " at row " + rowIndex);
                    if (policy == TypeMismatchPolicy.WARN_AND_SKIP) {
                        iterator.remove();
                        continue;
                    }
                    if (policy == TypeMismatchPolicy.SAFE_CONVERT) {
                        row.put(
                                actualField,
                                safeConvert(
                                        datasetId,
                                        fieldName,
                                        field.getType(),
                                        value,
                                        expectedType));
                        replaceSchemaField(
                                outputSchema, fieldName, expectedType);
                        continue;
                    }
                    throw typeError(
                            datasetId,
                            fieldName,
                            field.getType(),
                            value.getClass().getSimpleName());
                }
            }
            applyExpectedSchema(
                    outputSchema,
                    fieldName,
                    expectedType,
                    replaceScalarAlias);
        }
        return new ProcessedData(
                schemaFrom(outputSchema),
                rowsUnchanged(rows, outputRows)
                        ? rows : immutableRows(outputRows));
    }

    private static boolean matches(Class<?> expectedType, Object value) {
        return expectedType.isInstance(value);
    }

    private static boolean schemaMatches(
            Class<?> expectedType, Class<?> actualType) {
        return expectedType == Long.class
                ? actualType == Long.class
                : expectedType.equals(actualType);
    }

    private Object typedDefault(
            String datasetId,
            String fieldName,
            FieldDefinition field,
            Class<?> expectedType,
            ReportErrorCode errorCode) {
        if (!field.hasDefaultValue() || field.getDefaultValue() == null) {
            throw error(
                    errorCode,
                    datasetId,
                    "Field " + fieldName
                            + " requires an explicit non-null defaultValue");
        }
        Object value = field.getDefaultValue();
        if (matches(expectedType, value)) {
            return value;
        }
        try {
            return convert(value, expectedType);
        } catch (RuntimeException ex) {
            throw error(
                    errorCode,
                    datasetId,
                    "defaultValue for field " + fieldName
                            + " is not safely convertible to "
                            + normalizedType(field.getType()));
        }
    }

    private static Object safeConvert(
            String datasetId,
            String fieldName,
            String configuredType,
            Object value,
            Class<?> expectedType) {
        try {
            return convert(value, expectedType);
        } catch (RuntimeException ex) {
            throw typeError(
                    datasetId,
                    fieldName,
                    configuredType,
                    value.getClass().getSimpleName());
        }
    }

    private static Object convert(Object value, Class<?> expectedType) {
        if (expectedType.isInstance(value)) {
            return value;
        }
        if (expectedType == BigDecimal.class
                && (value instanceof Number || value instanceof String)) {
            return new BigDecimal(String.valueOf(value));
        }
        if (expectedType == Long.class
                && (value instanceof Number || value instanceof String)) {
            return new BigDecimal(String.valueOf(value)).longValueExact();
        }
        if (expectedType == Boolean.class && value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("not a boolean");
        }
        if (expectedType == LocalDate.class && value instanceof String) {
            return LocalDate.parse(((String) value).trim());
        }
        if (expectedType == LocalTime.class && value instanceof String) {
            return LocalTime.parse(((String) value).trim());
        }
        if (expectedType == LocalDateTime.class) {
            if (value instanceof String) {
                return LocalDateTime.parse(((String) value).trim());
            }
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            }
        }
        throw new IllegalArgumentException(
                "unsupported safe conversion from "
                        + value.getClass().getName()
                        + " to " + expectedType.getName());
    }

    private static boolean supportsConversion(
            Class<?> actualType, Class<?> expectedType) {
        if (expectedType.isAssignableFrom(actualType)) {
            return true;
        }
        if (expectedType == BigDecimal.class || expectedType == Long.class) {
            return Number.class.isAssignableFrom(actualType)
                    || actualType == String.class;
        }
        if (expectedType == Boolean.class
                || expectedType == LocalDate.class
                || expectedType == LocalTime.class) {
            return actualType == String.class;
        }
        if (expectedType == LocalDateTime.class) {
            return actualType == String.class
                    || Timestamp.class.isAssignableFrom(actualType);
        }
        return false;
    }

    private static ReportException typeError(
            String datasetId,
            String fieldName,
            String configuredType,
            String actualType) {
        return error(
                ReportErrorCode.DATA_003,
                datasetId,
                "Field " + fieldName + " in dataset " + datasetId
                        + " expected " + normalizedType(configuredType)
                        + " but was " + actualType);
    }

    private static List<LinkedHashMap<String, Object>> mutableRows(
            List<DatasetRow> rows) {
        List<LinkedHashMap<String, Object>> mutable =
                new ArrayList<LinkedHashMap<String, Object>>(rows.size());
        for (DatasetRow row : rows) {
            mutable.add(new LinkedHashMap<String, Object>(row.asMap()));
        }
        return mutable;
    }

    private static List<DatasetRow> immutableRows(
            List<LinkedHashMap<String, Object>> rows) {
        List<DatasetRow> immutable = new ArrayList<DatasetRow>(rows.size());
        for (Map<String, Object> row : rows) {
            Object[] pairs = new Object[row.size() * 2];
            int index = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                pairs[index++] = entry.getKey();
                pairs[index++] = entry.getValue();
            }
            immutable.add(DatasetRow.of(pairs));
        }
        return Collections.unmodifiableList(immutable);
    }

    private static boolean rowsUnchanged(
            List<DatasetRow> original,
            List<LinkedHashMap<String, Object>> processed) {
        if (original.size() != processed.size()) {
            return false;
        }
        for (int index = 0; index < original.size(); index++) {
            if (!original.get(index).asMap().equals(processed.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static DatasetSchema schemaFrom(
            LinkedHashMap<String, Class<?>> fields) {
        Object[] pairs = new Object[fields.size() * 2];
        int index = 0;
        for (Map.Entry<String, Class<?>> field : fields.entrySet()) {
            pairs[index++] = field.getKey();
            pairs[index++] = field.getValue();
        }
        return DatasetSchema.of(pairs);
    }

    private static String findField(Map<String, Object> row, String field) {
        for (String candidate : row.keySet()) {
            if (candidate.equalsIgnoreCase(field)) {
                return candidate;
            }
        }
        return null;
    }

    private static void replaceSchemaField(
            LinkedHashMap<String, Class<?>> schema,
            String field,
            Class<?> type) {
        String existing = null;
        for (String candidate : schema.keySet()) {
            if (candidate.equalsIgnoreCase(field)) {
                existing = candidate;
                break;
            }
        }
        if (existing == null) {
            schema.put(field, type);
        } else {
            schema.put(existing, type);
        }
    }

    private static void applyExpectedSchema(
            LinkedHashMap<String, Class<?>> schema,
            String field,
            Class<?> type,
            boolean replaceExistingFields) {
        if (replaceExistingFields) {
            schema.clear();
            schema.put(field, type);
            return;
        }
        replaceSchemaField(schema, field, type);
    }

    private static Class<?> resolveType(String fieldName, String type) {
        String normalized = normalizedType(type);
        Class<?> resolved = FIELD_TYPES.get(normalized);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Unsupported expected field type " + type
                            + " for " + fieldName);
        }
        return resolved;
    }

    private static String normalizedType(String type) {
        return requireText(type, "expected field type")
                .toUpperCase(Locale.ROOT);
    }

    private static DatasetResult buildResult(
            String datasetId,
            DatasetType resultType,
            DatasetSchema schema,
            List<DatasetRow> rows) {
        if (resultType == DatasetType.SCALAR) {
            return DatasetResult.scalar(datasetId, schema, rows);
        }
        if (resultType == DatasetType.SINGLE) {
            return DatasetResult.single(datasetId, schema, rows);
        }
        return DatasetResult.list(datasetId, schema, rows);
    }

    private static List<DatasetRow> copyRows(
            String datasetId, List<DatasetRow> sourceRows) {
        if (sourceRows == null) {
            throw new IllegalArgumentException(
                    "Dataset rows must not be null: " + datasetId);
        }
        List<DatasetRow> rows = new ArrayList<DatasetRow>(sourceRows.size());
        for (DatasetRow row : sourceRows) {
            if (row == null) {
                throw new IllegalArgumentException(
                        "Dataset rows must not contain null: " + datasetId);
            }
            rows.add(row);
        }
        return Collections.unmodifiableList(rows);
    }

    private static ReportException error(
            ReportErrorCode code, String datasetId, String message) {
        return new ReportException(
                code, message, null, "DATASET_QUERY", null, datasetId, null);
    }

    private static String requireText(String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return value;
    }

    private static Map<String, Class<?>> fieldTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<String, Class<?>>();
        types.put("STRING", String.class);
        types.put("INTEGER", Long.class);
        types.put("LONG", Long.class);
        types.put("DECIMAL", BigDecimal.class);
        types.put("DATE", LocalDate.class);
        types.put("TIME", LocalTime.class);
        types.put("DATETIME", LocalDateTime.class);
        types.put("TIMESTAMP", LocalDateTime.class);
        types.put("BOOLEAN", Boolean.class);
        types.put("BYTES", byte[].class);
        types.put("BINARY", byte[].class);
        return Collections.unmodifiableMap(types);
    }

    private static final class ProcessedData {
        private final DatasetSchema schema;
        private final List<DatasetRow> rows;

        private ProcessedData(
                DatasetSchema schema, List<DatasetRow> rows) {
            this.schema = schema;
            this.rows = rows;
        }
    }
}
