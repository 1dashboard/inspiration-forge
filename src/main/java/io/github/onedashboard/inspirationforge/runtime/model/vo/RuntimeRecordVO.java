package io.github.onedashboard.inspirationforge.runtime.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class RuntimeRecordVO implements Serializable {
    private String id;
    private String modelKey;
    private Map<String, Object> data;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
