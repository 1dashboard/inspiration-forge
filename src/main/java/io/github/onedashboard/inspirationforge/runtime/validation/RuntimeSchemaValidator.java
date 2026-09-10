package io.github.onedashboard.inspirationforge.runtime.validation;

import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeFieldDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RuntimeSchemaValidator {

    public static final int MAX_FIELDS = 100;
    public static final int MAX_RECORD_BYTES = 1024 * 1024;
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> RESERVED_FIELDS = Set.of(
            "id", "app_id", "environment", "model_key", "record_version",
            "create_time", "update_time", "is_delete");

    public RuntimeModelDefinition normalizeDefinition(RuntimeModelDefinition source) {
        if (source == null) throw error("数据模型不能为空");
        String modelKey = normalizeKey(source.getModelKey(), "模型标识");
        String displayName = normalizeText(source.getDisplayName(), "模型名称", 128);
        List<RuntimeFieldDefinition> sourceFields = source.getFields();
        if (sourceFields == null || sourceFields.isEmpty()) throw error("数据模型至少需要一个字段");
        if (sourceFields.size() > MAX_FIELDS) throw error("单个模型最多包含 " + MAX_FIELDS + " 个字段");

        RuntimeModelDefinition normalized = new RuntimeModelDefinition();
        normalized.setModelKey(modelKey);
        normalized.setDisplayName(displayName);
        normalized.setPublicOperations(source.getPublicOperations() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(source.getPublicOperations()));

        Set<String> keys = new HashSet<>();
        List<RuntimeFieldDefinition> fields = new ArrayList<>();
        for (RuntimeFieldDefinition sourceField : sourceFields) {
            if (sourceField == null) throw error("字段定义不能为空");
            String fieldKey = normalizeKey(sourceField.getKey(), "字段标识");
            if (RESERVED_FIELDS.contains(fieldKey)) throw error("字段标识为系统保留名称：" + fieldKey);
            if (!keys.add(fieldKey)) throw error("字段标识重复：" + fieldKey);
            if (sourceField.getType() == null) throw error("字段类型不能为空：" + fieldKey);

            RuntimeFieldDefinition field = new RuntimeFieldDefinition();
            field.setKey(fieldKey);
            field.setName(normalizeText(sourceField.getName(), "字段名称", 128));
            field.setType(sourceField.getType());
            field.setRequired(Boolean.TRUE.equals(sourceField.getRequired()));
            normalizeFieldOptions(sourceField, field);
            fields.add(field);
        }
        normalized.setFields(fields);
        return normalized;
    }

    public Map<String, Object> validateRecord(RuntimeModelDefinition definition, Map<String, Object> source) {
        Map<String, Object> values = source == null ? Map.of() : source;
        Map<String, RuntimeFieldDefinition> fieldMap = new LinkedHashMap<>();
        definition.getFields().forEach(field -> fieldMap.put(field.getKey(), field));
        for (String key : values.keySet()) {
            if (!fieldMap.containsKey(key)) throw error("数据包含模型中不存在的字段：" + key);
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (RuntimeFieldDefinition field : definition.getFields()) {
            Object value = values.get(field.getKey());
            if (value == null) {
                if (Boolean.TRUE.equals(field.getRequired())) throw error("必填字段不能为空：" + field.getName());
                continue;
            }
            validateValue(field, value);
            normalized.put(field.getKey(), value);
        }
        return normalized;
    }

    private void normalizeFieldOptions(RuntimeFieldDefinition source, RuntimeFieldDefinition target) {
        RuntimeFieldType type = source.getType();
        if (type == RuntimeFieldType.STRING || type == RuntimeFieldType.TEXT) {
            int defaultLength = type == RuntimeFieldType.STRING ? 255 : 20_000;
            int maxLength = source.getMaxLength() == null ? defaultLength : source.getMaxLength();
            if (maxLength < 1 || maxLength > 100_000) throw error("字段长度限制必须在 1 到 100000 之间");
            target.setMaxLength(maxLength);
        }
        if (type == RuntimeFieldType.ENUM) {
            List<String> options = source.getOptions() == null ? List.of() : source.getOptions().stream()
                    .map(value -> normalizeText(value, "枚举选项", 128))
                    .distinct()
                    .toList();
            if (options.isEmpty() || options.size() > 100) throw error("枚举字段需要 1 到 100 个选项");
            target.setOptions(options);
        } else {
            target.setOptions(List.of());
        }
    }

    private void validateValue(RuntimeFieldDefinition field, Object value) {
        switch (field.getType()) {
            case STRING, TEXT -> {
                if (!(value instanceof String text)) throw typeError(field, "字符串");
                if (text.length() > field.getMaxLength()) {
                    throw error("字段长度超过限制：" + field.getName() + "（最多 " + field.getMaxLength() + " 字符）");
                }
            }
            case INTEGER -> {
                if (!(value instanceof Number number)
                        || new BigDecimal(number.toString()).stripTrailingZeros().scale() > 0) {
                    throw typeError(field, "整数");
                }
            }
            case DECIMAL -> {
                if (!(value instanceof Number)) throw typeError(field, "数字");
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) throw typeError(field, "布尔值");
            }
            case DATE -> parseDate(field, value, DateTimeFormatter.ISO_LOCAL_DATE, "ISO 日期，例如 2026-08-22");
            case DATETIME -> parseDate(field, value, DateTimeFormatter.ISO_DATE_TIME,
                    "ISO 日期时间，例如 2026-08-22T14:30:00");
            case ENUM -> {
                if (!(value instanceof String text) || !field.getOptions().contains(text)) {
                    throw error("枚举字段值无效：" + field.getName());
                }
            }
        }
    }

    private void parseDate(RuntimeFieldDefinition field, Object value, DateTimeFormatter formatter, String expected) {
        if (!(value instanceof String text)) throw typeError(field, expected);
        try {
            if (field.getType() == RuntimeFieldType.DATE) LocalDate.parse(text, formatter);
            else formatter.parse(text);
        } catch (DateTimeParseException e) {
            throw typeError(field, expected);
        }
    }

    private String normalizeKey(String value, String label) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw error(label + "必须以小写字母开头，只能包含小写字母、数字和下划线，最长 64 位");
        }
        return normalized;
    }

    private String normalizeText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw error(label + "不能为空且最长为 " + maxLength + " 字符");
        }
        return normalized;
    }

    private BusinessException typeError(RuntimeFieldDefinition field, String expected) {
        return error("字段类型错误：" + field.getName() + " 应为" + expected);
    }

    private BusinessException error(String message) {
        return new BusinessException(ErrorCode.PARAMS_ERROR, message);
    }
}
