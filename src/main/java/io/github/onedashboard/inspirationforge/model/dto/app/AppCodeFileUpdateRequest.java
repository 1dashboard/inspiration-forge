package io.github.onedashboard.inspirationforge.model.dto.app;

import lombok.Data;

/** Request payload for saving a file edited in the application workspace. */
@Data
public class AppCodeFileUpdateRequest {
    private Long appId;
    private String filePath;
    private String content;
}
