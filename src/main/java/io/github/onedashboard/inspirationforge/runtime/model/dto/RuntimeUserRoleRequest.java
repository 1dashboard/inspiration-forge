package io.github.onedashboard.inspirationforge.runtime.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuntimeUserRoleRequest implements Serializable {
    private Long appId;
    private Long userId;
    private String roleKey;
}
