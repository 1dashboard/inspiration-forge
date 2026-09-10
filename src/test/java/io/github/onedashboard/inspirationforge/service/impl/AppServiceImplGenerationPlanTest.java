package io.github.onedashboard.inspirationforge.service.impl;

import io.github.onedashboard.inspirationforge.ai.model.GenerationPlan;
import io.github.onedashboard.inspirationforge.ai.model.GenerationDataField;
import io.github.onedashboard.inspirationforge.ai.model.GenerationDataModel;
import io.github.onedashboard.inspirationforge.ai.AiCodeGenTypeRoutingService;
import io.github.onedashboard.inspirationforge.ai.AiCodeGenTypeRoutingServiceFactory;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.enums.CodeGenTypeEnum;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppServiceImplGenerationPlanTest {

    private final AppServiceImpl service = new AppServiceImpl();

    @Test
    void fallsBackToVueWhenAiRoutingIsUnavailable() {
        AiCodeGenTypeRoutingServiceFactory factory = mock(AiCodeGenTypeRoutingServiceFactory.class);
        AiCodeGenTypeRoutingService routingService = mock(AiCodeGenTypeRoutingService.class);
        when(factory.createAiCodeGenTypeRoutingService()).thenReturn(routingService);
        when(routingService.routeCodeGenType("创建话题社区"))
                .thenThrow(new RuntimeException("Insufficient Balance"));
        ReflectionTestUtils.setField(service, "aiCodeGenTypeRoutingServiceFactory", factory);

        assertEquals(CodeGenTypeEnum.VUE_PROJECT, service.selectCodeGenType("创建话题社区"));
    }

    @Test
    void rejectsDatabasePlanWithoutModels() {
        GenerationPlan plan = new GenerationPlan();
        plan.setTitle("待办清单助手");
        plan.setSummary("保存待办任务");
        plan.setNeedsDatabase(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.validateGeneratedPlan(plan));

        assertEquals("计划需要数据库，但没有返回数据模型", error.getMessage());
    }

    @Test
    void createsEditableTodoFallbackWithDatabaseModel() {
        App app = new App();
        app.setAppName("待办清单助手");
        app.setCodeGenType("vue_project");

        GenerationPlan plan = service.buildFallbackPlan(app,
                "创建待办事项应用，任务保存到数据库，刷新后数据不能丢失");

        assertTrue(plan.getNeedsDatabase());
        assertEquals(1, plan.getDataModels().size());
        assertEquals("tasks", plan.getDataModels().getFirst().getModelKey());
        assertEquals(4, plan.getDataModels().getFirst().getFields().size());
        assertTrue(plan.getDataModels().getFirst().getPublicOperations().isEmpty());
    }

    @Test
    void createsCompleteSocialFallbackWithoutUserSchemaInput() {
        App app = new App();
        app.setAppName("话题社区");
        app.setCodeGenType("vue_project");

        GenerationPlan plan = service.buildFallbackPlan(app,
                "创建话题社区，支持发帖、评论、回复、点赞和消息通知");

        assertTrue(plan.getNeedsDatabase());
        assertEquals(List.of("topic", "comment", "notification"),
                plan.getDataModels().stream().map(GenerationDataModel::getModelKey).toList());
        assertTrue(plan.getDataModels().stream().allMatch(model -> !model.getFields().isEmpty()));
        service.validateGeneratedPlan(plan);
    }

    @Test
    void respectsExplicitDatabaseOptOut() {
        App app = new App();
        app.setAppName("静态页面");
        app.setCodeGenType("vue_project");

        GenerationPlan plan = service.buildFallbackPlan(app, "纯前端页面，不需要数据库");

        assertEquals(false, plan.getNeedsDatabase());
        assertTrue(plan.getDataModels().isEmpty());
    }

    @Test
    void normalizesAiKeysAndRestrictsAnonymousPermissions() {
        App app = new App();
        app.setCodeGenType("vue_project");
        GenerationDataField dueDate = new GenerationDataField();
        dueDate.setKey("dueDate");
        dueDate.setName("截止日期");
        dueDate.setType(RuntimeFieldType.DATETIME);
        dueDate.setMaxLength(0);
        GenerationDataModel task = new GenerationDataModel();
        task.setModelKey("Task Items");
        task.setDisplayName("任务");
        task.setFields(List.of(dueDate));
        task.setPublicOperations(new LinkedHashSet<>(List.of(RuntimeOperation.READ,
                RuntimeOperation.CREATE, RuntimeOperation.UPDATE, RuntimeOperation.DELETE)));
        GenerationPlan plan = new GenerationPlan();
        plan.setTitle("任务应用");
        plan.setSummary("管理任务");
        plan.setNeedsDatabase(true);
        plan.setDataModels(List.of(task));

        service.normalizeGeneratedPlan(plan, app, "保存任务到数据库");

        assertEquals("task_items", task.getModelKey());
        assertEquals("due_date", dueDate.getKey());
        assertEquals(null, dueDate.getMaxLength());
        assertTrue(task.getPublicOperations().isEmpty());
        assertEquals(List.of("Vue 3", "平台托管数据 SDK"), plan.getTechStack());
    }

    @Test
    void removesPlatformManagedFieldsFromAiPlan() {
        App app = new App();
        app.setCodeGenType("vue_project");
        GenerationDataField id = new GenerationDataField();
        id.setKey("id");
        id.setName("ID");
        id.setType(RuntimeFieldType.STRING);
        GenerationDataField title = new GenerationDataField();
        title.setKey("title");
        title.setName("标题");
        title.setType(RuntimeFieldType.STRING);
        GenerationDataModel model = new GenerationDataModel();
        model.setModelKey("topics");
        model.setDisplayName("话题");
        model.setFields(List.of(id, title));
        GenerationPlan plan = new GenerationPlan();
        plan.setNeedsDatabase(true);
        plan.setDataModels(List.of(model));

        service.normalizeGeneratedPlan(plan, app, "创建话题社区");

        assertEquals(List.of("title"), model.getFields().stream().map(GenerationDataField::getKey).toList());
    }
}
