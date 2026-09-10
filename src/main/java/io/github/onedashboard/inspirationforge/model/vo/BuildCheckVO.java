package io.github.onedashboard.inspirationforge.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildCheckVO implements Serializable {
    private Boolean success;
    private String stage;
    private String output;
    private Long durationMs;
    private LocalDateTime checkedAt;
}
