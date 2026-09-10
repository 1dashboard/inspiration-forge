package io.github.onedashboard.inspirationforge.model.dto.app;

import lombok.Data;

@Data
public class GenerationPlanRequest {
    private Long appId;
    private String prompt;
}
