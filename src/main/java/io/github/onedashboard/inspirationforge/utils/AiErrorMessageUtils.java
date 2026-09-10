package io.github.onedashboard.inspirationforge.utils;

import cn.hutool.core.util.StrUtil;

/** Converts provider errors into messages that are safe to persist and show to users. */
public final class AiErrorMessageUtils {

    private AiErrorMessageUtils() {
    }

    public static String friendly(Throwable error) {
        if (error == null) return friendly((String) null);
        StringBuilder detail = new StringBuilder(String.valueOf(error));
        Throwable cause = error.getCause();
        while (cause != null) {
            detail.append(' ').append(cause);
            cause = cause.getCause();
        }
        return friendly(detail.toString());
    }

    public static String friendly(String errorMessage) {
        String detail = StrUtil.blankToDefault(errorMessage, "AI 生成服务暂时不可用，请稍后重试。");
        String normalized = detail.toLowerCase();
        if (normalized.contains("arrearage") || normalized.contains("overdue-payment")
                || normalized.contains("insufficient balance") || normalized.contains("insufficient_balance")) {
            return "AI 服务暂时不可用，请检查模型账户余额或 API Key 配置后重试。";
        }
        if (normalized.contains("401") || normalized.contains("unauthorized")
                || normalized.contains("invalid api key") || normalized.contains("authentication")) {
            return "AI 服务认证失败，请检查 API Key 配置后重试。";
        }
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return "AI 服务响应超时，请稍后重试。";
        }
        if (detail.trim().startsWith("{") || normalized.contains("request_id")
                || normalized.contains("chatcmpl-") || normalized.contains("invalid_request_error")) {
            return "AI 生成服务暂时不可用，请稍后重试。";
        }
        return detail;
    }
}
