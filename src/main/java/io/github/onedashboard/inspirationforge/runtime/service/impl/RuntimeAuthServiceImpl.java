package io.github.onedashboard.inspirationforge.runtime.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppRoleMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppSessionMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppUserMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppUserRoleMapper;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.runtime.model.auth.RuntimeUserPrincipal;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeAuthRequest;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeApp;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeAppRole;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeAppSession;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeAppUser;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeAppUserRole;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeAuthVO;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeAuthService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class RuntimeAuthServiceImpl implements RuntimeAuthService {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_DAYS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private RuntimeAppMapper runtimeAppMapper;
    @Resource
    private RuntimeAppUserMapper runtimeAppUserMapper;
    @Resource
    private RuntimeAppRoleMapper roleMapper;
    @Resource
    private RuntimeAppUserRoleMapper userRoleMapper;
    @Resource
    private RuntimeAppSessionMapper sessionMapper;
    @Resource
    private AppMapper appMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuntimeAuthVO register(String runtimeKey, RuntimeAuthRequest request) {
        RuntimeApp app = requireApp(runtimeKey);
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String password = requirePassword(request == null ? null : request.getPassword());
        if (runtimeAppUserMapper.selectCountByQuery(QueryWrapper.create().eq("appId", app.getAppId()).eq("username", username)) > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        RuntimeAppUser user = new RuntimeAppUser();
        user.setAppId(app.getAppId());
        user.setUsername(username);
        user.setPasswordHash(hashPassword(password));
        user.setDisplayName(blankToDefault(request.getDisplayName(), username));
        user.setStatus("ACTIVE");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        if (runtimeAppUserMapper.insert(user) != 1) throw operation("注册失败");

        boolean firstUser = runtimeAppUserMapper.selectCountByQuery(QueryWrapper.create().eq("appId", app.getAppId())) == 1;
        RuntimeAppRole role = ensureRole(app.getAppId(), firstUser ? "admin" : "user",
                firstUser ? "管理员" : "用户");
        assignRole(app.getAppId(), user.getId(), role.getId());
        return issueSession(user, Set.of(role.getRoleKey()));
    }

    @Override
    public RuntimeAuthVO login(String runtimeKey, RuntimeAuthRequest request) {
        RuntimeApp app = requireApp(runtimeKey);
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String password = requirePassword(request == null ? null : request.getPassword());
        RuntimeAppUser user = runtimeAppUserMapper.selectOneByQuery(QueryWrapper.create()
                .eq("appId", app.getAppId()).eq("username", username));
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !verifyPassword(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户名或密码错误");
        }
        return issueSession(user, loadRoles(app.getAppId(), user.getId()));
    }

    @Override
    public RuntimeUserPrincipal optionalUser(String runtimeKey, HttpServletRequest request) {
        if (request == null) return null;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) return null;
        RuntimeApp app;
        try {
            app = requireApp(runtimeKey);
        } catch (BusinessException ignored) {
            return null;
        }
        RuntimeAppSession session = sessionMapper.selectOneByQuery(QueryWrapper.create()
                .eq("appId", app.getAppId()).eq("tokenHash", tokenHash(token)));
        if (session == null || session.getExpiresAt() == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        RuntimeAppUser user = runtimeAppUserMapper.selectOneByQuery(QueryWrapper.create()
                .eq("appId", app.getAppId()).eq("id", session.getUserId()));
        if (user == null || !"ACTIVE".equals(user.getStatus())) return null;
        session.setLastSeenAt(LocalDateTime.now());
        sessionMapper.update(session);
        return principal(user, loadRoles(app.getAppId(), user.getId()));
    }

    @Override
    public void logout(String runtimeKey, HttpServletRequest request) {
        if (request == null) return;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return;
        RuntimeApp app = requireApp(runtimeKey);
        String token = authorization.substring("Bearer ".length()).trim();
        sessionMapper.deleteByQuery(QueryWrapper.create().eq("appId", app.getAppId())
                .eq("tokenHash", tokenHash(token)));
    }

    @Override
    public List<RuntimeAuthVO> listUsers(Long appId, User platformUser) {
        requireOwner(appId, platformUser);
        return runtimeAppUserMapper.selectListByQuery(QueryWrapper.create().eq("appId", appId).orderBy("createTime", false))
                .stream().map(user -> RuntimeAuthVO.builder().userId(String.valueOf(user.getId())).appId(appId)
                        .username(user.getUsername()).displayName(user.getDisplayName()).roles(loadRoles(appId, user.getId())).build()).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long appId, Long userId, String roleKey, User platformUser) {
        requireOwner(appId, platformUser);
        if (userId == null || roleKey == null || !roleKey.matches("[a-z][a-z0-9_-]{1,63}"))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色参数无效");
        RuntimeAppUser target = runtimeAppUserMapper.selectOneByQuery(QueryWrapper.create().eq("appId", appId).eq("id", userId));
        if (target == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用用户不存在");
        RuntimeAppRole role = ensureRole(appId, roleKey, roleKey);
        userRoleMapper.deleteByQuery(QueryWrapper.create().eq("appId", appId).eq("userId", userId));
        assignRole(appId, userId, role.getId());
    }

    private void requireOwner(Long appId, User platformUser) {
        if (platformUser == null || appId == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "平台用户未登录");
        App app = appMapper.selectOneById(appId);
        if (app == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(platformUser.getId())) throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权管理该应用用户");
    }

    private RuntimeApp requireApp(String runtimeKey) {
        if (runtimeKey == null || !runtimeKey.matches("[a-f0-9]{32}")) throw new BusinessException(ErrorCode.PARAMS_ERROR, "运行面标识无效");
        RuntimeApp app = runtimeAppMapper.selectOneByQuery(QueryWrapper.create().eq("runtimeKey", runtimeKey));
        if (app == null || !Boolean.TRUE.equals(app.getEnabled())) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        return app;
    }

    private RuntimeAuthVO issueSession(RuntimeAppUser user, Set<String> roles) {
        String token = randomToken();
        RuntimeAppSession session = new RuntimeAppSession();
        session.setAppId(user.getAppId());
        session.setUserId(user.getId());
        session.setTokenHash(tokenHash(token));
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        session.setCreateTime(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());
        if (sessionMapper.insert(session) != 1) throw operation("登录会话创建失败");
        return RuntimeAuthVO.builder().token(token).userId(String.valueOf(user.getId())).appId(user.getAppId())
                .username(user.getUsername()).displayName(user.getDisplayName()).roles(roles).build();
    }

    private RuntimeUserPrincipal principal(RuntimeAppUser user, Set<String> roles) {
        return RuntimeUserPrincipal.builder().id(user.getId()).appId(user.getAppId()).username(user.getUsername())
                .displayName(user.getDisplayName()).roles(roles).build();
    }

    private RuntimeAppRole ensureRole(Long appId, String roleKey, String displayName) {
        RuntimeAppRole role = roleMapper.selectOneByQuery(QueryWrapper.create().eq("appId", appId).eq("roleKey", roleKey));
        if (role != null) return role;
        role = new RuntimeAppRole();
        role.setAppId(appId); role.setRoleKey(roleKey); role.setDisplayName(displayName);
        role.setCreateTime(LocalDateTime.now()); role.setUpdateTime(LocalDateTime.now());
        if (roleMapper.insert(role) != 1) throw operation("角色创建失败");
        return role;
    }

    private void assignRole(Long appId, Long userId, Long roleId) {
        RuntimeAppUserRole relation = new RuntimeAppUserRole();
        relation.setAppId(appId); relation.setUserId(userId); relation.setRoleId(roleId); relation.setCreateTime(LocalDateTime.now());
        if (userRoleMapper.insert(relation) != 1) throw operation("角色绑定失败");
    }

    private Set<String> loadRoles(Long appId, Long userId) {
        Set<String> roles = new LinkedHashSet<>();
        userRoleMapper.selectListByQuery(QueryWrapper.create().eq("appId", appId).eq("userId", userId))
                .forEach(relation -> {
                    RuntimeAppRole role = roleMapper.selectOneById(relation.getRoleId());
                    if (role != null) roles.add(role.getRoleKey());
                });
        return roles;
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim().toLowerCase();
        if (!value.matches("[a-z0-9_][a-z0-9_.-]{2,63}")) throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名需为 3-64 位字母、数字或符号");
        return value;
    }

    private String requirePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度需为 8-128 位");
        return password;
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[SALT_BYTES]; RANDOM.nextBytes(salt);
            byte[] hash = derive(password.toCharArray(), salt, PBKDF2_ITERATIONS);
            return PBKDF2_ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) { throw operation("密码处理失败"); }
    }

    private boolean verifyPassword(String password, String encoded) {
        try {
            String[] parts = encoded.split(":");
            if (parts.length != 3) return false;
            byte[] expected = derive(password.toCharArray(), Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0]));
            return MessageDigest.isEqual(expected, Base64.getDecoder().decode(parts[2]));
        } catch (Exception e) { return false; }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) throws Exception {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private String randomToken() { byte[] bytes = new byte[TOKEN_BYTES]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String tokenHash(String token) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw operation("令牌处理失败"); } }
    private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().substring(0, Math.min(128, value.trim().length())); }
    private BusinessException operation(String message) { return new BusinessException(ErrorCode.OPERATION_ERROR, message); }
}
