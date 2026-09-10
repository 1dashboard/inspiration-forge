package io.github.onedashboard.inspirationforge.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class CodeFileDiffVO {
    private String path;
    private String status;
    private String beforeContent;
    private String afterContent;
    private Integer additions;
    private Integer deletions;
    private String unifiedDiff;
    private List<CodeDiffLineVO> lines;
}
