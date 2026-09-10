package io.github.onedashboard.inspirationforge.model.dto.app;

import io.github.onedashboard.inspirationforge.ai.model.GenerationPlan;
import lombok.Data;

/** Starts a generation task without putting prompts or plans in the SSE URL. */
@Data
public class CodeGenerationRequest {
    private Long appId;
    private String message;
    private GenerationPlan plan;
}
