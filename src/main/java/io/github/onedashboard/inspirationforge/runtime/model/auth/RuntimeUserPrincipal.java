package io.github.onedashboard.inspirationforge.runtime.model.auth;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class RuntimeUserPrincipal {
    Long id;
    Long appId;
    String username;
    String displayName;
    Set<String> roles;
}
