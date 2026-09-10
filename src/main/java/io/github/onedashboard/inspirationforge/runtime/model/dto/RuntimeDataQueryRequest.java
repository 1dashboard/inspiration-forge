package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeDataQueryRequest implements Serializable {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
