package io.github.onedashboard.inspirationforge.model.dto.app;

import lombok.Data;

@Data
public class AppVersionFileRestoreRequest {
    private Long appId;
    private Long versionId;
    private String filePath;
}
