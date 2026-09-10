package io.github.onedashboard.inspirationforge.runtime.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class RuntimeConfigVO implements Serializable {
    private String runtimeKey;
    private Boolean enabled;
    private String previewBaseUrl;
    private String productionBaseUrl;
}
