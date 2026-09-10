package io.github.onedashboard.inspirationforge.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.onedashboard.inspirationforge.mapper.GenerationTaskMapper;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.GenerationTask;
import io.github.onedashboard.inspirationforge.service.GenerationTaskService;
import io.github.onedashboard.inspirationforge.service.AppVersionService;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

@Service
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTask>
        implements GenerationTaskService {
    @Resource
    private AppMapper appMapper;

    @Resource
    private AppVersionService appVersionService;

    /** An in-memory stream cannot survive a process restart; expose it as resumable. */
    @PostConstruct
    public void recoverInterruptedTasks() {
        list(QueryWrapper.create().eq("status", "GENERATING")).forEach(task -> {
            task.setStatus("PAUSED");
            task.setCurrentStep("服务重启，等待恢复");
            updateById(task);
            var version = appVersionService.getLatestGenerating(task.getAppId());
            if (version != null) appVersionService.markCancelled(version);
            App app = appMapper.selectOneById(task.getAppId());
            if (app != null && "GENERATING".equals(app.getGenerationStatus())) {
                App update = new App();
                update.setId(app.getId());
                update.setGenerationStatus("CANCELLED");
                appMapper.update(update);
            }
        });
    }
    @Override
    public GenerationTask getLatestByAppId(Long appId) {
        return getOne(QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("createTime", false)
                .limit(1));
    }
}
