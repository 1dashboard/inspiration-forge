package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeRecordQueryRequest implements Serializable {
    private Long appId;
    private String environment;
    private String modelKey;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
