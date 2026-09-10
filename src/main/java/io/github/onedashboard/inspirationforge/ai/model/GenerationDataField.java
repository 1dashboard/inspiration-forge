package io.github.onedashboard.inspirationforge.ai.model;

import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GenerationDataField {
    private String key;
    private String name;
    private RuntimeFieldType type = RuntimeFieldType.STRING;
    private Boolean required = false;
    private Integer maxLength;
    private List<String> options = new ArrayList<>();
}
