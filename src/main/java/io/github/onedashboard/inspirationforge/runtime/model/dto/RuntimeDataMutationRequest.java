package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RuntimeDataMutationRequest implements Serializable {
    private Map<String, Object> data = new LinkedHashMap<>();
    private Integer expectedVersion;
}
