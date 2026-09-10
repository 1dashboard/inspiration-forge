package io.github.onedashboard.inspirationforge.runtime.model.schema;

import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Data
public class RuntimeModelDefinition implements Serializable {
    private String modelKey;
    private String displayName;
    private List<RuntimeFieldDefinition> fields = new ArrayList<>();
    private Set<RuntimeOperation> publicOperations = new LinkedHashSet<>();
    private Map<String, Set<RuntimeOperation>> rolePermissions = new java.util.LinkedHashMap<>();
}
