package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RuntimeRecordCreateRequest implements Serializable {
    private Long appId;
    private String environment;
    private String modelKey;
    private Map<String, Object> data = new LinkedHashMap<>();
}
