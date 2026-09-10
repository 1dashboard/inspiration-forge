package io.github.onedashboard.inspirationforge.runtime.validation;

import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeFieldDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSchemaValidatorTest {

    private final RuntimeSchemaValidator validator = new RuntimeSchemaValidator();

    @Test
    void normalizesKeysAndKeepsPublicOperationsClosedByDefault() {
        RuntimeModelDefinition definition = definition(field("Title", RuntimeFieldType.STRING, true));

        RuntimeModelDefinition normalized = validator.normalizeDefinition(definition);

        assertEquals("tasks", normalized.getModelKey());
        assertEquals("title", normalized.getFields().getFirst().getKey());
        assertEquals(255, normalized.getFields().getFirst().getMaxLength());
        assertTrue(normalized.getPublicOperations().isEmpty());
    }

    @Test
    void rejectsReservedAndDuplicateFieldKeys() {
        RuntimeModelDefinition reserved = definition(field("id", RuntimeFieldType.STRING, false));
        assertThrows(BusinessException.class, () -> validator.normalizeDefinition(reserved));

        RuntimeModelDefinition duplicate = definition(
                field("title", RuntimeFieldType.STRING, false),
                field("TITLE", RuntimeFieldType.TEXT, false));
        assertThrows(BusinessException.class, () -> validator.normalizeDefinition(duplicate));
    }

    @Test
    void rejectsUnknownMissingAndInvalidValues() {
        RuntimeModelDefinition normalized = validator.normalizeDefinition(
                definition(field("title", RuntimeFieldType.STRING, true)));

        assertThrows(BusinessException.class,
                () -> validator.validateRecord(normalized, Map.of("unknown", "value")));
        assertThrows(BusinessException.class,
                () -> validator.validateRecord(normalized, Map.of()));
        assertThrows(BusinessException.class,
                () -> validator.validateRecord(normalized, Map.of("title", 42)));
    }

    @Test
    void validatesEnumOptionsAndIntegerValues() {
        RuntimeFieldDefinition status = field("status", RuntimeFieldType.ENUM, true);
        status.setOptions(List.of("todo", "done"));
        RuntimeModelDefinition normalized = validator.normalizeDefinition(
                definition(status, field("priority", RuntimeFieldType.INTEGER, false)));

        Map<String, Object> values = validator.validateRecord(normalized,
                Map.of("status", "todo", "priority", 2));
        assertEquals("todo", values.get("status"));
        assertThrows(BusinessException.class,
                () -> validator.validateRecord(normalized, Map.of("status", "invalid")));
        assertThrows(BusinessException.class,
                () -> validator.validateRecord(normalized, Map.of("status", "todo", "priority", 1.5)));
    }

    private RuntimeModelDefinition definition(RuntimeFieldDefinition... fields) {
        RuntimeModelDefinition definition = new RuntimeModelDefinition();
        definition.setModelKey(" Tasks ");
        definition.setDisplayName("Tasks");
        definition.setFields(List.of(fields));
        return definition;
    }

    private RuntimeFieldDefinition field(String key, RuntimeFieldType type, boolean required) {
        RuntimeFieldDefinition field = new RuntimeFieldDefinition();
        field.setKey(key);
        field.setName(key);
        field.setType(type);
        field.setRequired(required);
        return field;
    }
}
