package com.xn.report.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Executes the Draft-07 keyword subset used by report-definition.schema.json.
 * It also rejects unsupported schema keywords so the contract cannot silently
 * stop validating when the schema grows.
 */
public final class JsonSchemaContract {

    private static final Set<String> SUPPORTED_KEYWORDS =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "$schema", "$id", "$ref", "title", "description", "type",
                    "additionalProperties", "required", "properties", "definitions",
                    "oneOf", "items", "minItems", "maxItems",
                    "uniqueItems", "enum",
                    "minLength", "maxLength", "pattern", "minimum", "maximum",
                    "exclusiveMinimum",
                    "x-java-maxUtf16Length", "x-java-nonBlank",
                    "x-java-stackGroupConsistency",
                    "x-java-stockTemplateOnly",
                    "x-java-chartDataLabelDefaults",
                    "x-java-chartPointLimits",
                    "x-java-chartExcelMode",
                    "x-java-chartSeriesPropertyMatrix",
                    "x-java-wordTocUpdate",
                    "x-java-wordAttachmentContent",
                    "x-java-wordNumberingLevels")));

    private final JsonNode rootSchema;

    public JsonSchemaContract(JsonNode rootSchema) {
        if (rootSchema == null || !rootSchema.isObject()) {
            throw new IllegalArgumentException("Root schema must be an object");
        }
        this.rootSchema = rootSchema;
        assertSupportedSchema(rootSchema, "#");
    }

    public List<String> validate(JsonNode instance) {
        List<String> errors = new ArrayList<String>();
        validate(rootSchema, instance, "$", errors);
        return Collections.unmodifiableList(errors);
    }

    private void validate(
            JsonNode schema,
            JsonNode instance,
            String path,
            List<String> errors) {
        if (schema.has("$ref")) {
            validate(resolve(schema.path("$ref").asText()), instance, path, errors);
            return;
        }
        if (!matchesType(schema.path("type").asText(null), instance)) {
            errors.add(path + " must be of type " + schema.path("type").asText());
            return;
        }

        validateEnum(schema, instance, path, errors);
        validateOneOf(schema, instance, path, errors);
        validateChartExtensions(schema, instance, path, errors);
        if (instance.isObject()) {
            validateObject(schema, instance, path, errors);
        }
        if (instance.isArray()) {
            validateArray(schema, instance, path, errors);
        }
        if (instance.isTextual()) {
            validateString(schema, instance.asText(), path, errors);
        }
        if (instance.isNumber()) {
            validateNumber(schema, instance.decimalValue(), path, errors);
        }
    }

    private void validateChartExtensions(
            JsonNode schema,
            JsonNode instance,
            String path,
            List<String> errors) {
        if (!instance.isObject()) {
            return;
        }
        if (schema.path("x-java-stackGroupConsistency").asBoolean(false)) {
            Map<String, String> types = new java.util.LinkedHashMap<String, String>();
            Map<String, String> axes = new java.util.LinkedHashMap<String, String>();
            Map<String, String> stackSlots =
                    new java.util.LinkedHashMap<String, String>();
            boolean primaryPercent = false;
            boolean primaryOrdinary = false;
            boolean secondaryPercent = false;
            boolean secondaryOrdinary = false;
            for (JsonNode series : instance.path("series")) {
                String type = enumName(series.path("type").asText());
                String axis = enumName(series.path("axis").asText("PRIMARY"));
                boolean percent = "PERCENT_STACKED_COLUMN".equals(type);
                boolean axisBearing = !"PIE".equals(type)
                        && !"DOUGHNUT".equals(type)
                        && !"RADAR".equals(type);
                if (axisBearing) {
                    if ("SECONDARY".equals(axis)) {
                        secondaryPercent |= percent;
                        secondaryOrdinary |= !percent;
                    } else {
                        primaryPercent |= percent;
                        primaryOrdinary |= !percent;
                    }
                }
                String group = series.path("stackGroup").asText("");
                String renderSlot = renderSlot(type);
                if (renderSlot != null) {
                    String slot = renderSlot + "|" + axis;
                    String token = group.trim().isEmpty() ? type : group;
                    String previousGroup = stackSlots.put(slot, token);
                    if (previousGroup != null
                            && !previousGroup.equals(token)) {
                        errors.add(path
                                + ".series has conflicting groups in "
                                + renderSlot + " on " + axis
                                + " or multiple stackGroup values");
                    }
                }
                if (group.trim().isEmpty()) {
                    continue;
                }
                if (types.containsKey(group)
                        && !types.get(group).equals(type)) {
                    errors.add(path + ".series has mixed types in stackGroup " + group);
                }
                if (axes.containsKey(group)
                        && !axes.get(group).equals(axis)) {
                    errors.add(path + ".series has mixed axes in stackGroup " + group);
                }
                types.put(group, type);
                axes.put(group, axis);
            }
            if (primaryPercent && primaryOrdinary) {
                errors.add(path
                        + ".series shares a percent PRIMARY axis with ordinary values");
            }
            if (secondaryPercent && secondaryOrdinary) {
                errors.add(path
                        + ".series shares a percent SECONDARY axis with ordinary values");
            }
        }
        if (schema.path("x-java-stockTemplateOnly").asBoolean(false)) {
            boolean stock = false;
            for (JsonNode series : instance.path("series")) {
                stock |= "STOCK".equals(
                        enumName(series.path("type").asText()));
            }
            if (stock && !"TEMPLATE_NATIVE".equals(
                    enumName(instance.path("mode").asText(
                            "GENERATED_NATIVE")))) {
                errors.add(path + ".mode must be TEMPLATE_NATIVE for STOCK");
            }
        }
        if (schema.path("x-java-chartDataLabelDefaults")
                .asBoolean(false)) {
            String chartLabels = enumName(
                    instance.path("dataLabelMode").asText("NONE"));
            for (JsonNode series : instance.path("series")) {
                if (!series.has("format")) {
                    continue;
                }
                String labels = series.has("dataLabels")
                        ? enumName(series.path("dataLabels").asText())
                        : chartLabels;
                if ("NONE".equals(labels)) {
                    errors.add(path
                            + ".series format requires visible dataLabels");
                }
            }
        }
        if (schema.path("x-java-chartPointLimits").asBoolean(false)) {
            long categories = instance.path("categories").isArray()
                    ? instance.path("categories").size() : 0L;
            long series = instance.path("series").isArray()
                    ? instance.path("series").size() : 0L;
            if (categories * series > 200000L) {
                errors.add(path
                        + " configured categories and series exceed MAX_POINTS=200000");
            }
        }
        if (schema.path("x-java-chartExcelMode").asBoolean(false)) {
            String mode = enumName(instance.path("mode")
                    .asText("GENERATED_NATIVE"));
            boolean marker = instance.has("templateChartMarker");
            boolean index = instance.has("templateChartIndex");
            boolean locators = instance.has("templateChartLocators");
            boolean grouped = instance.has("groupByField");
            if ("TEMPLATE_NATIVE".equals(mode)) {
                if (!instance.has("excelSheet")) {
                    errors.add(path
                            + ".excelSheet is required for TEMPLATE_NATIVE");
                }
                if (grouped && !locators) {
                    errors.add(path
                            + ".templateChartLocators is required "
                            + "with groupByField");
                } else if (grouped && (marker || index)) {
                    errors.add(path
                            + " grouped locators cannot be combined "
                            + "with a legacy locator");
                } else if (!grouped && locators) {
                    errors.add(path
                            + ".templateChartLocators requires groupByField");
                } else if (!grouped && marker == index) {
                    errors.add(path
                            + " requires exactly one template chart locator");
                }
                if (instance.has("anchorRow")
                        || instance.has("anchorColumn")
                        || instance.has("anchorWidthColumns")
                        || instance.has("anchorHeightRows")) {
                    errors.add(path
                            + " template chart cannot configure an anchor");
                }
            } else if (marker || index || locators) {
                errors.add(path
                        + " template chart locator requires TEMPLATE_NATIVE");
            }
        }
        if (schema.path("x-java-chartSeriesPropertyMatrix")
                .asBoolean(false)) {
            String type = enumName(instance.path("type").asText());
            if (("SCATTER".equals(type) || "BUBBLE".equals(type))
                    && (instance.has("lineStyle")
                    || instance.has("lineWidth"))) {
                errors.add(path + " has unsupported line properties for " + type);
            }
            if ((instance.has("lineStyle") || instance.has("lineWidth"))
                    && !"LINE".equals(type)
                    && !"AREA".equals(type)
                    && !"STACKED_AREA".equals(type)
                    && !"RADAR".equals(type)
                    && !"STOCK".equals(type)) {
                errors.add(path + " has unsupported line properties for " + type);
            }
            if ("BUBBLE".equals(type) && instance.has("marker")) {
                errors.add(path + ".marker is unsupported for BUBBLE");
            }
            if ("RADAR".equals(type)
                    && (instance.has("marker")
                    || instance.has("dataLabels")
                    || instance.has("format")
                    || instance.has("axis"))) {
                errors.add(path + " has unsupported RADAR properties");
            }
            if (instance.has("marker")
                    && !"LINE".equals(type)
                    && !"SCATTER".equals(type)
                    && !"STOCK".equals(type)) {
                errors.add(path + ".marker is unsupported for " + type);
            }
            if ("SCATTER".equals(type)
                    && instance.has("marker")
                    && !instance.path("marker").asBoolean()) {
                errors.add(path + ".marker must remain visible for SCATTER");
            }
            if (("PIE".equals(type) || "DOUGHNUT".equals(type))
                    && instance.has("format")) {
                errors.add(path + ".format is unsupported for " + type);
            }
            String labels = enumName(
                    instance.path("dataLabels").asText("NONE"));
            if (!"PIE".equals(type) && !"DOUGHNUT".equals(type)
                    && instance.has("dataLabels")
                    && !"NONE".equals(labels) && !"VALUE".equals(labels)) {
                errors.add(path + ".dataLabels supports only VALUE for " + type);
            }
            if (("PIE".equals(type) || "DOUGHNUT".equals(type)
                    || "RADAR".equals(type)) && instance.has("axis")) {
                errors.add(path + ".axis is unsupported for " + type);
            }
        }
        if (schema.path("x-java-wordTocUpdate").asBoolean(false)
                && instance.path("enabled").asBoolean(false)
                && (!instance.has("updateOnOpen")
                || !instance.path("updateOnOpen").asBoolean(false))) {
            errors.add(path
                    + ".updateOnOpen must be true when the TOC is enabled");
        }
        if (schema.path("x-java-wordAttachmentContent").asBoolean(false)
                && "ATTACHMENT".equals(instance.path("type").asText())) {
            boolean content = nonBlank(instance.path("title"))
                    || nonBlank(instance.path("description"));
            for (JsonNode item : instance.path("items")) {
                content |= nonBlank(item);
            }
            if (!content) {
                errors.add(path
                        + " ATTACHMENT requires a title, description, or item");
            }
        }
        if (schema.path("x-java-wordNumberingLevels").asBoolean(false)
                && instance.path("levels").isArray()) {
            Set<Integer> levels = new LinkedHashSet<Integer>();
            for (JsonNode level : instance.path("levels")) {
                levels.add(Integer.valueOf(level.path("level").asInt()));
            }
            if (!levels.equals(new LinkedHashSet<Integer>(
                    Arrays.asList(1, 2, 3, 4)))) {
                errors.add(path
                        + ".levels must uniquely contain levels 1 through 4");
            }
        }
    }

    private static boolean nonBlank(JsonNode value) {
        return value.isTextual() && !value.asText().trim().isEmpty();
    }

    private static String enumName(String value) {
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static String renderSlot(String type) {
        if ("COLUMN".equals(type) || "STACKED_COLUMN".equals(type)
                || "PERCENT_STACKED_COLUMN".equals(type)) {
            return "VERTICAL_COLUMN";
        }
        if ("BAR".equals(type) || "STACKED_BAR".equals(type)) {
            return "HORIZONTAL_BAR";
        }
        if ("AREA".equals(type) || "STACKED_AREA".equals(type)) {
            return "VERTICAL_AREA";
        }
        return null;
    }

    private void validateObject(
            JsonNode schema,
            JsonNode instance,
            String path,
            List<String> errors) {
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode name : required) {
                if (!instance.has(name.asText())) {
                    errors.add(path + " is missing required property " + name.asText());
                }
            }
        }

        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> propertySchemas = properties.fields();
            while (propertySchemas.hasNext()) {
                Map.Entry<String, JsonNode> property = propertySchemas.next();
                if (instance.has(property.getKey())) {
                    validate(property.getValue(), instance.get(property.getKey()),
                            path + "." + property.getKey(), errors);
                }
            }
        }

        JsonNode additionalProperties = schema.get("additionalProperties");
        if (additionalProperties == null || additionalProperties.isBoolean()
                && additionalProperties.asBoolean()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = instance.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (properties.has(field.getKey())) {
                continue;
            }
            if (additionalProperties.isBoolean()) {
                errors.add(path + " has unknown property " + field.getKey());
            } else {
                validate(additionalProperties, field.getValue(),
                        path + "." + field.getKey(), errors);
            }
        }
    }

    private void validateArray(
            JsonNode schema,
            JsonNode instance,
            String path,
            List<String> errors) {
        if (schema.has("minItems") && instance.size() < schema.path("minItems").asInt()) {
            errors.add(path + " has fewer than " + schema.path("minItems").asInt()
                    + " items");
        }
        if (schema.has("maxItems")
                && instance.size() > schema.path("maxItems").asInt()) {
            errors.add(path + " has more than "
                    + schema.path("maxItems").asInt() + " items");
        }
        if (schema.path("uniqueItems").asBoolean(false)) {
            Set<JsonNode> unique = new LinkedHashSet<JsonNode>();
            for (JsonNode item : instance) {
                if (!unique.add(item)) {
                    errors.add(path + " contains duplicate items");
                }
            }
        }
        JsonNode itemSchema = schema.get("items");
        if (itemSchema != null && itemSchema.isObject()) {
            for (int index = 0; index < instance.size(); index++) {
                validate(itemSchema, instance.get(index),
                        path + "[" + index + "]", errors);
            }
        }
    }

    private void validateString(
            JsonNode schema,
            String value,
            String path,
            List<String> errors) {
        int codePointLength = value.codePointCount(0, value.length());
        if (schema.path("x-java-nonBlank").asBoolean(false)
                && value.trim().isEmpty()) {
            errors.add(path + " must not be blank using Java trim semantics");
        }
        if (schema.has("x-java-maxUtf16Length")
                && value.length() > schema.path("x-java-maxUtf16Length").asInt()) {
            errors.add(path + " exceeds Java UTF-16 length limit");
        }
        if (schema.has("minLength")
                && codePointLength < schema.path("minLength").asInt()) {
            errors.add(path + " is shorter than minLength");
        }
        if (schema.has("maxLength")
                && codePointLength > schema.path("maxLength").asInt()) {
            errors.add(path + " is longer than maxLength");
        }
        if (schema.has("pattern")
                && !Pattern.compile(schema.path("pattern").asText())
                        .matcher(value).find()) {
            errors.add(path + " does not match pattern");
        }
    }

    private void validateNumber(
            JsonNode schema,
            BigDecimal value,
            String path,
            List<String> errors) {
        if (schema.has("minimum")
                && value.compareTo(schema.path("minimum").decimalValue()) < 0) {
            errors.add(path + " is less than minimum");
        }
        if (schema.has("exclusiveMinimum")
                && value.compareTo(
                        schema.path("exclusiveMinimum").decimalValue()) <= 0) {
            errors.add(path + " is not greater than exclusiveMinimum");
        }
        if (schema.has("maximum")
                && value.compareTo(schema.path("maximum").decimalValue()) > 0) {
            errors.add(path + " is greater than maximum");
        }
    }

    private void validateEnum(
            JsonNode schema,
            JsonNode instance,
            String path,
            List<String> errors) {
        JsonNode values = schema.path("enum");
        if (!values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            if (value.equals(instance)) {
                return;
            }
        }
        errors.add(path + " is not an allowed enum value");
    }

    private void validateOneOf(
            JsonNode schema,
            JsonNode instance,
            String path,
            List<String> errors) {
        JsonNode alternatives = schema.path("oneOf");
        if (!alternatives.isArray()) {
            return;
        }
        int matches = 0;
        for (JsonNode alternative : alternatives) {
            List<String> alternativeErrors = new ArrayList<String>();
            validate(alternative, instance, path, alternativeErrors);
            if (alternativeErrors.isEmpty()) {
                matches++;
            }
        }
        if (matches != 1) {
            errors.add(path + " must match exactly one oneOf alternative");
        }
    }

    private boolean matchesType(String type, JsonNode instance) {
        if (type == null || type.isEmpty()) {
            return true;
        }
        if ("object".equals(type)) {
            return instance.isObject();
        }
        if ("array".equals(type)) {
            return instance.isArray();
        }
        if ("string".equals(type)) {
            return instance.isTextual();
        }
        if ("integer".equals(type)) {
            return instance.isIntegralNumber();
        }
        if ("number".equals(type)) {
            return instance.isNumber();
        }
        if ("boolean".equals(type)) {
            return instance.isBoolean();
        }
        throw new IllegalArgumentException("Unsupported schema type: " + type);
    }

    private JsonNode resolve(String reference) {
        if (!reference.startsWith("#/")) {
            throw new IllegalArgumentException("Only local schema references are supported");
        }
        JsonNode resolved = rootSchema.at(reference.substring(1));
        if (resolved.isMissingNode()) {
            throw new IllegalArgumentException("Unknown schema reference: " + reference);
        }
        return resolved;
    }

    private void assertSupportedSchema(JsonNode schema, String path) {
        Iterator<String> names = schema.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!SUPPORTED_KEYWORDS.contains(name)) {
                throw new IllegalArgumentException(
                        "Unsupported schema keyword at " + path + ": " + name);
            }
        }
        assertSchemaMap(schema.path("definitions"), path + "/definitions");
        assertSchemaMap(schema.path("properties"), path + "/properties");
        assertSchemaArray(schema.path("oneOf"), path + "/oneOf");
        JsonNode items = schema.path("items");
        if (items.isObject()) {
            assertSupportedSchema(items, path + "/items");
        }
        JsonNode additional = schema.path("additionalProperties");
        if (additional.isObject()) {
            assertSupportedSchema(additional, path + "/additionalProperties");
        }
    }

    private void assertSchemaMap(JsonNode schemas, String path) {
        if (!schemas.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = schemas.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            assertSupportedSchema(field.getValue(), path + "/" + field.getKey());
        }
    }

    private void assertSchemaArray(JsonNode schemas, String path) {
        if (!schemas.isArray()) {
            return;
        }
        for (int index = 0; index < schemas.size(); index++) {
            assertSupportedSchema(schemas.get(index), path + "/" + index);
        }
    }
}
