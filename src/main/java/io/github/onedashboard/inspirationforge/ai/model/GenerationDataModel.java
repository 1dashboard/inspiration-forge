package io.github.onedashboard.inspirationforge.ai.model;

import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Data
public class GenerationDataModel {
    private String modelKey;
    private String displayName;
    private List<GenerationDataField> fields = new ArrayList<>();
    private Set<RuntimeOperation> publicOperations = new LinkedHashSet<>();
    private Map<String, Set<RuntimeOperation>> rolePermissions = new java.util.LinkedHashMap<>();
}
