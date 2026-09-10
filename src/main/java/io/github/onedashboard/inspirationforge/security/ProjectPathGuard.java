package io.github.onedashboard.inspirationforge.security;

import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Centralizes project path validation for AI tools and user edits. */
@Component
public class ProjectPathGuard {
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PROJECT_FILES = 5000;

    public Path resolveToolPath(Long appId, String relativePath) {
        return resolve(appId, "vue_project", relativePath, false);
    }

    public Path resolveGeneratedPath(Long appId, String codeGenType, String relativePath) {
        return resolve(appId, codeGenType, relativePath, false);
    }

    public Path resolveGeneratedRoot(Long appId, String codeGenType) {
        return root(appId, codeGenType);
    }

    public boolean isPlatformManagedFile(String relativePath) {
        if (relativePath == null) return false;
        return "src/lib/yuruntime.js".equals(relativePath.trim().replace('\\', '/').toLowerCase());
    }

    public void validateFileSize(Path path) {
        try {
            if (Files.exists(path) && Files.size(path) > MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小超过 5 MB 限制");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法检查文件大小");
        }
    }

    private Path resolve(Long appId, String codeGenType, String relativePath, boolean allowRoot) {
        if (appId == null || appId <= 0 || codeGenType == null || codeGenType.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目参数无效");
        }
        String normalized = normalizeRelativePath(relativePath, allowRoot);
        Path root = root(appId, codeGenType);
        Path candidate = root.resolve(normalized).normalize();
        if (!candidate.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径超出项目目录");
        }
        rejectSymlink(candidate, root);
        return candidate;
    }

    private Path root(Long appId, String codeGenType) {
        String safeType = codeGenType.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeType.isBlank()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目类型无效");
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, safeType + "_" + appId)
                .toAbsolutePath().normalize();
    }

    private String normalizeRelativePath(String relativePath, boolean allowRoot) {
        if (relativePath == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        String value = relativePath.trim().replace('\\', '/');
        if (value.isBlank()) {
            if (allowRoot) return "";
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        }
        if (value.startsWith("/") || value.matches("^[a-zA-Z]:.*") || value.contains("\0")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只允许使用项目相对路径");
        }
        for (String part : value.split("/")) {
            if (part.equals("..") || part.equals(".")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径包含非法片段");
            }
            if (part.equalsIgnoreCase(".env") || part.equalsIgnoreCase(".git")
                    || part.equalsIgnoreCase("node_modules")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许访问受保护目录或文件");
            }
        }
        return value;
    }

    private void rejectSymlink(Path candidate, Path root) {
        Path current = root;
        if (Files.exists(root) && Files.isSymbolicLink(root)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目目录不允许使用符号链接");
        }
        Path relative = root.relativize(candidate);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current) && Files.isSymbolicLink(current)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径不允许经过符号链接");
            }
        }
    }
}
