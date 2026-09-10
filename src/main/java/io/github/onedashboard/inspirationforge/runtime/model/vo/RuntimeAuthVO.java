package io.github.onedashboard.inspirationforge.runtime.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

@Data
@Builder
public class RuntimeAuthVO implements Serializable {
    private String token;
    private String userId;
    private Long appId;
    private String username;
    private String displayName;
    private Set<String> roles;
}
