package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RuntimeRecordUpdateRequest implements Serializable {
    private Long appId;
    private String environment;
    private String modelKey;
    private Long recordId;
    private Integer expectedVersion;
    private Map<String, Object> data = new LinkedHashMap<>();
}
