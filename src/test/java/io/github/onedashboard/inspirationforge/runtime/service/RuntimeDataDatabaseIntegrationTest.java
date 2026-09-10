package io.github.onedashboard.inspirationforge.runtime.service;

import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeFieldDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class RuntimeDataDatabaseIntegrationTest {

    @Resource
    private AppMapper appMapper;

    @Resource
    private RuntimeDataService runtimeDataService;

    @Test
    void createsRuntimeConfigAndModelWithDatabaseConstraints() {
        LocalDateTime now = LocalDateTime.now();
        App app = new App();
        app.setAppName("runtime-integration-test");
        app.setCodeGenType("vue_project");
        app.setVersionNumber(0);
        app.setGenerationStatus("IDLE");
        app.setDeployStatus("NOT_DEPLOYED");
        app.setPriority(0);
        app.setUserId(1L);
        app.setEditTime(now);
        app.setCreateTime(now);
        app.setUpdateTime(now);
        app.setIsDelete(0);
        appMapper.insert(app);
        assertNotNull(app.getId());
        User owner = new User();
        owner.setId(app.getUserId());

        assertNotNull(runtimeDataService.ensureConfig(app.getId(), owner).getRuntimeKey());

        RuntimeFieldDefinition title = new RuntimeFieldDefinition();
        title.setKey("title");
        title.setName("标题");
        title.setType(RuntimeFieldType.STRING);
        title.setRequired(true);
        title.setMaxLength(100);
        RuntimeModelDefinition definition = new RuntimeModelDefinition();
        definition.setModelKey("task");
        definition.setDisplayName("任务");
        definition.setFields(List.of(title));
        definition.setPublicOperations(new LinkedHashSet<>());
        RuntimeModelUpsertRequest request = new RuntimeModelUpsertRequest();
        request.setAppId(app.getId());
        request.setDefinition(definition);

        assertNotNull(runtimeDataService.upsertPreviewModel(request, owner).getId());
    }
}
