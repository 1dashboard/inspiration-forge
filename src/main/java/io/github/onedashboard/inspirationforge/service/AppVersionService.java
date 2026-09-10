package io.github.onedashboard.inspirationforge.service;

import com.mybatisflex.core.service.IService;
import io.github.onedashboard.inspirationforge.model.entity.AppVersion;

import java.util.List;

public interface AppVersionService extends IService<AppVersion> {
    AppVersion startVersion(Long appId, String codeGenType, String sourcePath, String changeMessage);
    void markReady(AppVersion version);

    void markFailed(AppVersion version);

    void markCancelled(AppVersion version);
    List<AppVersion> listByAppId(Long appId);

    AppVersion getLatestCancelled(Long appId);

    AppVersion getLatestGenerating(Long appId);

    boolean deleteByAppId(Long appId);
}
