package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeAuthRequest implements Serializable {
    private Long appId;
    private String username;
    private String password;
    private String displayName;
}
