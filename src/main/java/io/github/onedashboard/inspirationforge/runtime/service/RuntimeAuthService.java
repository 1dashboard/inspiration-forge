package io.github.onedashboard.inspirationforge.runtime.service;

import io.github.onedashboard.inspirationforge.runtime.model.auth.RuntimeUserPrincipal;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeAuthRequest;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeAuthVO;
import io.github.onedashboard.inspirationforge.model.entity.User;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

public interface RuntimeAuthService {
    RuntimeAuthVO register(String runtimeKey, RuntimeAuthRequest request);

    RuntimeAuthVO login(String runtimeKey, RuntimeAuthRequest request);

    RuntimeUserPrincipal optionalUser(String runtimeKey, HttpServletRequest request);

    void logout(String runtimeKey, HttpServletRequest request);

    List<RuntimeAuthVO> listUsers(Long appId, User platformUser);

    void assignRole(Long appId, Long userId, String roleKey, User platformUser);
}
