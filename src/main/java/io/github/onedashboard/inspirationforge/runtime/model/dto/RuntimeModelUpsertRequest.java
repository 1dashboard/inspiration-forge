package io.github.onedashboard.inspirationforge.runtime.model.dto;

import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeModelUpsertRequest implements Serializable {
    private Long appId;
    private RuntimeModelDefinition definition;
}
