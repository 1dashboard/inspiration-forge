package io.github.onedashboard.inspirationforge.runtime.model.schema;

import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuntimeFieldDefinition implements Serializable {
    private String key;
    private String name;
    private RuntimeFieldType type;
    private Boolean required = false;
    private Integer maxLength;
    private List<String> options = new ArrayList<>();
}
