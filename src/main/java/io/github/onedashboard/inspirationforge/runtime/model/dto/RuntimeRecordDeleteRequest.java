package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeRecordDeleteRequest implements Serializable {
    private Long appId;
    private String environment;
    private String modelKey;
    private Long recordId;
    private Integer expectedVersion;
}
