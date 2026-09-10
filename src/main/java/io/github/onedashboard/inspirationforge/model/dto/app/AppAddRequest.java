package io.github.onedashboard.inspirationforge.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /** Whether project code and conversation facts should be used as long-term memory. */
    private Boolean ragEnabled;

    private static final long serialVersionUID = 1L;
}
