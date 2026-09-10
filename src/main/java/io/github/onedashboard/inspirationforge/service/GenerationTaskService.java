package io.github.onedashboard.inspirationforge.service;

import com.mybatisflex.core.service.IService;
import io.github.onedashboard.inspirationforge.model.entity.GenerationTask;

public interface GenerationTaskService extends IService<GenerationTask> {
    GenerationTask getLatestByAppId(Long appId);
}
