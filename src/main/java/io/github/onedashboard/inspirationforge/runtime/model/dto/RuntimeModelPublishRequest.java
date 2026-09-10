package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeModelPublishRequest implements Serializable {
    private Long appId;
    private String modelKey;
}
