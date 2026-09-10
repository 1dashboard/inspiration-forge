package io.github.onedashboard.inspirationforge.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeDiffLineVO {
    private String type;
    private Integer beforeLineNumber;
    private Integer afterLineNumber;
    private String beforeContent;
    private String afterContent;
}
