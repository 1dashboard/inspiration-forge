package io.github.onedashboard.inspirationforge.runtime.model.vo;

import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
public class RuntimeModelVO implements Serializable {
    private String id;
    private String environment;
    private RuntimeModelDefinition definition;
    private Integer schemaVersion;
    private String status;
    private LocalDateTime updateTime;
}
