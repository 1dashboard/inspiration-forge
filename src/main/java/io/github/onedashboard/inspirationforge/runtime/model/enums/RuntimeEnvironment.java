package io.github.onedashboard.inspirationforge.runtime.model.enums;

import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;

public enum RuntimeEnvironment {
    PREVIEW,
    PRODUCTION;

    public static RuntimeEnvironment parse(String value) {
        if (value == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "运行环境不能为空");
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "运行环境只支持 PREVIEW 或 PRODUCTION");
        }
    }
}
