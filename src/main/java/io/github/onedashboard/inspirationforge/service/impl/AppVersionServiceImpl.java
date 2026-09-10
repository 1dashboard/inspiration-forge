package io.github.onedashboard.inspirationforge.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.onedashboard.inspirationforge.mapper.AppVersionMapper;
import io.github.onedashboard.inspirationforge.model.entity.AppVersion;
import io.github.onedashboard.inspirationforge.service.AppVersionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion> implements AppVersionService {
    @Resource
    private io.github.onedashboard.inspirationforge.mapper.AppMapper appMapper;

    @Override
    public synchronized AppVersion startVersion(Long appId, String codeGenType, String sourcePath, String changeMessage) {
        AppVersion version = new AppVersion();
        version.setAppId(appId);
        version.setCodeGenType(codeGenType);
        version.setSourcePath(sourcePath);
        version.setChangeMessage(changeMessage);
        version.setStatus("GENERATING");
        var app = appMapper.selectOneByQuery(QueryWrapper.create().eq("id", appId));
        List<AppVersion> latestVersions = list(QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("versionNumber", false)
                .limit(1));
        Integer current = latestVersions.isEmpty() ? 0 : latestVersions.get(0).getVersionNumber();
        version.setVersionNumber((current == null ? 0 : current) + 1);
        save(version);
        if (app != null) {
            app.setVersionNumber(version.getVersionNumber());
            appMapper.update(app);
        }
        return version;
    }

    @Override
    public void markReady(AppVersion version) {
        version.setStatus("READY");
        updateById(version);
    }

    @Override
    public void markFailed(AppVersion version) {
        version.setStatus("FAILED");
        updateById(version);
    }

    @Override
    public void markCancelled(AppVersion version) {
        version.setStatus("CANCELLED");
        updateById(version);
    }

    @Override
    public List<AppVersion> listByAppId(Long appId) {
        return list(QueryWrapper.create().eq("appId", appId).orderBy("versionNumber", false));
    }

    @Override
    public AppVersion getLatestCancelled(Long appId) {
        List<AppVersion> versions = list(QueryWrapper.create()
                .eq("appId", appId)
                .eq("status", "CANCELLED")
                .orderBy("versionNumber", false)
                .limit(1));
        return versions.isEmpty() ? null : versions.getFirst();
    }

    @Override
    public AppVersion getLatestGenerating(Long appId) {
        List<AppVersion> versions = list(QueryWrapper.create()
                .eq("appId", appId)
                .eq("status", "GENERATING")
                .orderBy("versionNumber", false)
                .limit(1));
        return versions.isEmpty() ? null : versions.getFirst();
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        return getMapper().deletePermanentlyByAppId(appId) >= 0;
    }
}
