package io.github.onedashboard.inspirationforge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.onedashboard.inspirationforge.ai.AiCodeGenTypeRoutingService;
import io.github.onedashboard.inspirationforge.ai.AiCodeGenTypeRoutingServiceFactory;
import io.github.onedashboard.inspirationforge.ai.AiCodeGeneratorServiceFactory;
import io.github.onedashboard.inspirationforge.ai.GenerationMode;
import io.github.onedashboard.inspirationforge.ai.AiGenerationPlanService;
import io.github.onedashboard.inspirationforge.ai.model.GenerationPlan;
import io.github.onedashboard.inspirationforge.ai.model.GenerationDataField;
import io.github.onedashboard.inspirationforge.ai.model.GenerationDataModel;
import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileMessage;
import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.core.AiCodeGeneratorFacade;
import io.github.onedashboard.inspirationforge.core.builder.VueProjectBuilder;
import io.github.onedashboard.inspirationforge.core.handler.StreamHandlerExecutor;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.exception.ThrowUtils;
import io.github.onedashboard.inspirationforge.model.dto.app.AppAddRequest;
import io.github.onedashboard.inspirationforge.model.dto.app.AppCodeFileUpdateRequest;
import io.github.onedashboard.inspirationforge.model.dto.app.AppQueryRequest;
import io.github.onedashboard.inspirationforge.model.dto.app.AppVersionFileRestoreRequest;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.AppVersion;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.model.entity.GenerationTask;
import io.github.onedashboard.inspirationforge.model.enums.ChatHistoryMessageTypeEnum;
import io.github.onedashboard.inspirationforge.model.enums.CodeGenTypeEnum;
import io.github.onedashboard.inspirationforge.model.vo.AppVO;
import io.github.onedashboard.inspirationforge.model.vo.UserVO;
import io.github.onedashboard.inspirationforge.model.vo.CodeFileDiffVO;
import io.github.onedashboard.inspirationforge.model.vo.BuildCheckVO;
import io.github.onedashboard.inspirationforge.model.vo.CodeDiffLineVO;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import io.github.onedashboard.inspirationforge.service.AppService;
import io.github.onedashboard.inspirationforge.service.AppVersionService;
import io.github.onedashboard.inspirationforge.manager.CosManager;
import io.github.onedashboard.inspirationforge.service.ChatHistoryService;
import io.github.onedashboard.inspirationforge.service.ScreenshotService;
import io.github.onedashboard.inspirationforge.service.UserService;
import io.github.onedashboard.inspirationforge.security.ProjectPathGuard;
import io.github.onedashboard.inspirationforge.service.GenerationTaskService;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeDataService;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeSdkManager;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeFieldDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeConfigVO;
import io.github.onedashboard.inspirationforge.rag.service.RagKnowledgeService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import cn.hutool.json.JSONUtil;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author 1dashboard
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    private static final Set<String> RUNTIME_RESERVED_FIELDS = Set.of(
            "id", "app_id", "environment", "model_key", "record_version",
            "create_time", "update_time", "is_delete");

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AppVersionService appVersionService;

    @Resource
    private CosManager cosManager;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private GenerationTaskService generationTaskService;

    @Resource
    private ProjectPathGuard projectPathGuard;

    @Resource
    private AppMapper appMapper;

    @Resource
    private RuntimeDataService runtimeDataService;

    @Resource
    private RuntimeSdkManager runtimeSdkManager;

    @Resource
    private RagKnowledgeService ragKnowledgeService;

    /** 当前应用的生成订阅，用于停止生成时释放后端流。 */
    private final Map<Long, Disposable> generationSubscriptions = new ConcurrentHashMap<>();

    /** Browser connections can come and go while this backend-owned stream keeps running. */
    private final Map<Long, Sinks.Many<String>> generationStreams = new ConcurrentHashMap<>();

    /** 当前应用对应的版本记录，用于停止生成时更新版本状态。 */
    private final Map<Long, AppVersion> generationVersions = new ConcurrentHashMap<>();

    /** Latest build diagnostics are reused by the one-click repair action. */
    private final Map<Long, BuildCheckVO> latestBuildChecks = new ConcurrentHashMap<>();

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, GenerationPlan plan, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR,
                "无权限访问该应用");
        if ("GENERATING".equals(app.getGenerationStatus()) && generationStreams.containsKey(appId)) {
            return generationStreams.get(appId).asFlux();
        }
        ThrowUtils.throwIf("GENERATING".equals(app.getGenerationStatus()), ErrorCode.OPERATION_ERROR,
                "应用正在生成中，请刷新页面恢复任务状态");

        boolean hasExistingProject = hasUsableProject(app);
        GenerationMode generationMode = hasExistingProject ? GenerationMode.MODIFY : GenerationMode.CREATE;
        String aiPrompt = buildPromptWithPlan(app, message, plan, loginUser);
        return startGeneration(app, aiPrompt, message, message, loginUser, generationMode);
    }

    @Override
    public Flux<String> observeGeneration(Long appId, User loginUser) {
        App app = getOwnedApp(appId, loginUser);
        Sinks.Many<String> stream = generationStreams.get(appId);
        if (stream != null) {
            return stream.asFlux();
        }
        GenerationTask task = generationTaskService.getLatestByAppId(appId);
        if (task != null && "GENERATING".equals(task.getStatus())) {
            return Flux.error(new BusinessException(ErrorCode.OPERATION_ERROR,
                    "任务仍在运行，但实时流已不可用，请等待状态同步"));
        }
        // The provider can fail before the browser attaches to the stream. Replay the
        // persisted failure instead of returning an empty stream that looks successful.
        if (task != null && "FAILED".equals(task.getStatus())) {
            return Flux.error(new BusinessException(ErrorCode.SYSTEM_ERROR,
                    StrUtil.blankToDefault(task.getErrorMessage(), "AI 生成服务暂时不可用，请稍后重试")));
        }
        return Flux.empty();
    }

    private String buildPromptWithPlan(App app, String message, GenerationPlan plan, User loginUser) {
        if (plan == null) {
            return message;
        }
        plan.setTitle(StrUtil.subWithLength(StrUtil.trim(plan.getTitle()), 0, 80));
        plan.setSummary(StrUtil.subWithLength(StrUtil.trim(plan.getSummary()), 0, 500));
        plan.setPages(normalizePlanItems(plan.getPages()));
        plan.setFeatures(normalizePlanItems(plan.getFeatures()));
        plan.setTechStack(normalizePlanItems(plan.getTechStack()));
        ThrowUtils.throwIf(StrUtil.isBlank(plan.getTitle()) || StrUtil.isBlank(plan.getSummary()),
                ErrorCode.PARAMS_ERROR, "生成计划的应用名称和生成目标不能为空");
        RuntimeConfigVO runtimeConfig = prepareRuntimeModels(app, plan, loginUser);
        String authPlan = Boolean.TRUE.equals(plan.getNeedsAuth())
                ? "需要应用用户认证，使用 src/lib/yuRuntime.js 提供的 auth.register、auth.login、auth.me、auth.logout；角色：" + plan.getRoles()
                : "不需要应用用户认证，不要自行实现登录接口";
        String runtimePlan = runtimeConfig == null ? "不使用托管数据库，保持纯前端实现"
                : """
                使用平台托管数据库。必须调用平台提供的 src/lib/yuRuntime.js，不要自行拼接接口地址或实现另一套 SDK。
                运行时标识：%s
                数据模型：%s
                """.formatted(runtimeConfig.getRuntimeKey(), plan.getDataModels());
        return """
                %s

                以下是用户确认并可能手动编辑过的生成计划。实现时必须以此计划为准；若与原始需求冲突，
                优先遵循这份最终计划。不要在回复中重复计划，直接按计划完成代码生成。
                 <confirmed_generation_plan>
                 应用名称：%s
                 生成目标：%s
                 页面结构：%s
                 核心功能：%s
                 技术方案：%s
                 图片素材：%s
                 用户认证：%s
                 数据能力：%s
                </confirmed_generation_plan>
                """.formatted(message, plan.getTitle(), plan.getSummary(), plan.getPages(), plan.getFeatures(),
                plan.getTechStack(), Boolean.TRUE.equals(plan.getNeedsImages()) ? "需要" : "不需要", authPlan, runtimePlan);
    }

    private RuntimeConfigVO prepareRuntimeModels(App app, GenerationPlan plan, User loginUser) {
        if (!Boolean.TRUE.equals(plan.getNeedsDatabase())) {
            plan.setDataModels(List.of());
            return null;
        }
        ThrowUtils.throwIf(CollUtil.isEmpty(plan.getDataModels()), ErrorCode.PARAMS_ERROR,
                "需要数据库时至少配置一个数据模型");
        ThrowUtils.throwIf(plan.getDataModels().size() > 20, ErrorCode.PARAMS_ERROR,
                "单个应用最多配置 20 个数据模型");
        if (!CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())) {
            boolean hasExistingProject = (app.getVersionNumber() != null && app.getVersionNumber() > 0)
                    || app.getCurrentVersionId() != null;
            ThrowUtils.throwIf(hasExistingProject, ErrorCode.PARAMS_ERROR,
                    "已有静态项目暂不能直接启用托管数据库，请新建 Vue 工程应用");
            App update = new App();
            update.setId(app.getId());
            update.setCodeGenType(CodeGenTypeEnum.VUE_PROJECT.getValue());
            ThrowUtils.throwIf(!updateById(update), ErrorCode.OPERATION_ERROR, "切换 Vue 工程模式失败");
            app.setCodeGenType(CodeGenTypeEnum.VUE_PROJECT.getValue());
        }
        RuntimeConfigVO config = runtimeDataService.ensureConfig(app.getId(), loginUser);
        List<GenerationDataModel> normalizedModels = new ArrayList<>();
        for (GenerationDataModel source : plan.getDataModels()) {
            ThrowUtils.throwIf(source == null, ErrorCode.PARAMS_ERROR, "数据模型不能为空");
            RuntimeModelDefinition definition = new RuntimeModelDefinition();
            definition.setModelKey(source.getModelKey());
            definition.setDisplayName(source.getDisplayName());
            definition.setPublicOperations(source.getPublicOperations() == null
                    ? new LinkedHashSet<>() : new LinkedHashSet<>(source.getPublicOperations()));
            definition.setRolePermissions(source.getRolePermissions() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getRolePermissions()));
            List<RuntimeFieldDefinition> fields = new ArrayList<>();
            if (source.getFields() != null) {
                for (GenerationDataField sourceField : source.getFields()) {
                    ThrowUtils.throwIf(sourceField == null, ErrorCode.PARAMS_ERROR, "数据字段不能为空");
                    RuntimeFieldDefinition field = new RuntimeFieldDefinition();
                    field.setKey(sourceField.getKey());
                    field.setName(sourceField.getName());
                    field.setType(sourceField.getType());
                    field.setRequired(sourceField.getRequired());
                    field.setMaxLength(sourceField.getMaxLength());
                    field.setOptions(sourceField.getOptions());
                    fields.add(field);
                }
            }
            definition.setFields(fields);
            RuntimeModelUpsertRequest request = new RuntimeModelUpsertRequest();
            request.setAppId(app.getId());
            request.setDefinition(definition);
            RuntimeModelDefinition saved = runtimeDataService.upsertPreviewModel(request, loginUser).getDefinition();
            normalizedModels.add(toGenerationDataModel(saved));
        }
        plan.setDataModels(normalizedModels);
        runtimeSdkManager.sync(app.getId(), config);
        return config;
    }

    private GenerationDataModel toGenerationDataModel(RuntimeModelDefinition definition) {
        GenerationDataModel model = new GenerationDataModel();
        model.setModelKey(definition.getModelKey());
        model.setDisplayName(definition.getDisplayName());
        model.setPublicOperations(definition.getPublicOperations());
        model.setRolePermissions(definition.getRolePermissions());
        model.setFields(definition.getFields().stream().map(field -> {
            GenerationDataField result = new GenerationDataField();
            result.setKey(field.getKey());
            result.setName(field.getName());
            result.setType(field.getType());
            result.setRequired(field.getRequired());
            result.setMaxLength(field.getMaxLength());
            result.setOptions(field.getOptions());
            return result;
        }).toList());
        return model;
    }


    private List<String> normalizePlanItems(List<String> items) {
        if (CollUtil.isEmpty(items)) {
            return List.of();
        }
        return items.stream()
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .map(item -> StrUtil.subWithLength(item, 0, 80))
                .distinct()
                .limit(20)
                .toList();
    }

    @Override
    public Flux<String> resumeGeneration(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR,
                "无权限访问该应用");
        ThrowUtils.throwIf(!"CANCELLED".equals(app.getGenerationStatus()), ErrorCode.PARAMS_ERROR,
                "只有已中止的生成任务可以恢复");
        ThrowUtils.throwIf(generationSubscriptions.containsKey(appId), ErrorCode.OPERATION_ERROR,
                "应用正在生成中");
        ThrowUtils.throwIf(!hasUsableProject(app), ErrorCode.OPERATION_ERROR,
                "项目文件尚未生成，无法恢复修改任务，请重新生成应用");

        AppVersion cancelledVersion = appVersionService.getLatestCancelled(appId);
        ThrowUtils.throwIf(cancelledVersion == null, ErrorCode.NOT_FOUND_ERROR, "未找到可恢复的生成任务");
        String originalRequest = StrUtil.blankToDefault(cancelledVersion.getChangeMessage(), app.getInitPrompt());
        String resumePrompt = """
                继续完成上一次被中止的生成任务。
                原始需求：%s

                项目目录中可能已经存在部分生成文件。请先检查现有目录和文件，保留正确内容，
                补齐或修复未完成部分，确保项目完整、可运行。不要重复创建无关文件，完成后调用 exit 工具。
                """.formatted(originalRequest);
        return startGeneration(app, resumePrompt, "继续上一次中止的生成任务", originalRequest, loginUser,
                GenerationMode.MODIFY);
    }

    @Override
    public GenerationTask getLatestGenerationTask(Long appId, User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        GenerationTask task = generationTaskService.getLatestByAppId(appId);
        if (task != null && "FAILED".equals(task.getStatus())) {
            String safeMessage = friendlyGenerationError(task.getErrorMessage());
            if (!safeMessage.equals(task.getErrorMessage())) {
                task.setErrorMessage(safeMessage);
                generationTaskService.updateById(task);
            }
        }
        return task;
    }

    @Override
    public GenerationPlan createGenerationPlan(Long appId, String prompt, User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(prompt), ErrorCode.PARAMS_ERROR, "需求不能为空");
        Exception lastFailure = null;
        AiGenerationPlanService service = aiCodeGenTypeRoutingServiceFactory.createAiGenerationPlanService();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                GenerationPlan plan = service.generatePlan(prompt);
                normalizeGeneratedPlan(plan, app, prompt);
                validateGeneratedPlan(plan);
                return plan;
            } catch (Exception e) {
                lastFailure = e;
                log.warn("生成计划第 {} 次尝试失败，appId: {}，原因: {}", attempt, appId, e.getMessage());
            }
        }
        log.warn("生成计划连续失败，使用可编辑的本地兜底计划，appId: {}", appId, lastFailure);
        return buildFallbackPlan(app, prompt);
    }

    void normalizeGeneratedPlan(GenerationPlan plan, App app, String prompt) {
        if (plan == null) {
            return;
        }
        plan.setPages(normalizePlanItems(plan.getPages()));
        plan.setFeatures(normalizePlanItems(plan.getFeatures()));
        plan.setNeedsImages(Boolean.TRUE.equals(plan.getNeedsImages()));
        plan.setNeedsDatabase(Boolean.TRUE.equals(plan.getNeedsDatabase()));
        if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())) {
            plan.setTechStack(Boolean.TRUE.equals(plan.getNeedsDatabase())
                    ? List.of("Vue 3", "平台托管数据 SDK") : List.of("Vue 3"));
        } else {
            plan.setTechStack(normalizePlanItems(plan.getTechStack()));
        }
        if (!Boolean.TRUE.equals(plan.getNeedsDatabase())) {
            plan.setDataModels(List.of());
            return;
        }

        boolean allowsAnonymous = containsAny(prompt.toLowerCase(), "匿名", "无需登录", "免登录", "公开访问", "游客");
        List<GenerationDataModel> models = plan.getDataModels() == null ? new ArrayList<>() : plan.getDataModels();
        Set<String> modelKeys = new LinkedHashSet<>();
        for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
            GenerationDataModel model = models.get(modelIndex);
            if (model == null) {
                continue;
            }
            String modelKey = normalizeGeneratedKey(model.getModelKey(), "model_" + (modelIndex + 1));
            while (!modelKeys.add(modelKey)) {
                modelKey = normalizeGeneratedKey(modelKey + "_" + (modelIndex + 1), "model_" + (modelIndex + 1));
            }
            model.setModelKey(modelKey);
            Set<io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation> publicOperations = new LinkedHashSet<>();
            if (allowsAnonymous && model.getPublicOperations() != null) {
                if (model.getPublicOperations().contains(io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation.READ)) {
                    publicOperations.add(io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation.READ);
                }
                if (model.getPublicOperations().contains(io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation.CREATE)) {
                    publicOperations.add(io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation.CREATE);
                }
            }
            model.setPublicOperations(publicOperations);

            List<GenerationDataField> fields = model.getFields() == null ? new ArrayList<>() : model.getFields();
            List<GenerationDataField> normalizedFields = new ArrayList<>();
            Set<String> fieldKeys = new LinkedHashSet<>();
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                GenerationDataField field = fields.get(fieldIndex);
                if (field == null) {
                    continue;
                }
                String fieldKey = normalizeGeneratedKey(field.getKey(), "field_" + (fieldIndex + 1));
                if (RUNTIME_RESERVED_FIELDS.contains(fieldKey)) {
                    log.debug("忽略 AI 计划中的平台系统字段: {}", fieldKey);
                    continue;
                }
                while (!fieldKeys.add(fieldKey)) {
                    fieldKey = normalizeGeneratedKey(fieldKey + "_" + (fieldIndex + 1), "field_" + (fieldIndex + 1));
                }
                field.setKey(fieldKey);
                field.setRequired(Boolean.TRUE.equals(field.getRequired()));
                field.setType(field.getType() == null ? RuntimeFieldType.STRING : field.getType());
                if (field.getType() == RuntimeFieldType.STRING || field.getType() == RuntimeFieldType.TEXT) {
                    int defaultLength = field.getType() == RuntimeFieldType.STRING ? 255 : 20_000;
                    if (field.getMaxLength() == null || field.getMaxLength() < 1) {
                        field.setMaxLength(defaultLength);
                    } else {
                        field.setMaxLength(Math.min(field.getMaxLength(), 100_000));
                    }
                } else {
                    field.setMaxLength(null);
                }
                field.setOptions(field.getOptions() == null ? new ArrayList<>() : field.getOptions());
                normalizedFields.add(field);
            }
            model.setFields(normalizedFields);
        }
        plan.setDataModels(models);
    }

    private String normalizeGeneratedKey(String source, String fallback) {
        String key = StrUtil.blankToDefault(source, "")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (key.isEmpty()) {
            key = fallback;
        }
        if (!Character.isLetter(key.charAt(0))) {
            key = "field_" + key;
        }
        return StrUtil.subWithLength(key, 0, 64).replaceAll("_+$", "");
    }

    void validateGeneratedPlan(GenerationPlan plan) {
        if (plan == null || StrUtil.isBlank(plan.getTitle()) || StrUtil.isBlank(plan.getSummary())) {
            throw new IllegalStateException("生成计划缺少标题或目标");
        }
        if (!Boolean.TRUE.equals(plan.getNeedsDatabase())) {
            return;
        }
        if (CollUtil.isEmpty(plan.getDataModels())) {
            throw new IllegalStateException("计划需要数据库，但没有返回数据模型");
        }
        boolean hasInvalidModel = plan.getDataModels().stream().anyMatch(model -> model == null
                || StrUtil.isBlank(model.getModelKey()) || StrUtil.isBlank(model.getDisplayName())
                || CollUtil.isEmpty(model.getFields()));
        if (hasInvalidModel) {
            throw new IllegalStateException("计划包含不完整的数据模型");
        }
    }

    GenerationPlan buildFallbackPlan(App app, String prompt) {
        GenerationPlan fallback = new GenerationPlan();
        fallback.setTitle(StrUtil.blankToDefault(app.getAppName(), "新应用"));
        fallback.setSummary(prompt);
        fallback.setNeedsImages(false);

        boolean needsDatabase = needsDatabaseFallback(prompt);
        fallback.setNeedsDatabase(needsDatabase);
        if (!needsDatabase) {
            fallback.setPages(List.of("主页面"));
            fallback.setFeatures(List.of("根据需求生成页面和交互"));
            fallback.setTechStack(List.of(app.getCodeGenType()));
            return fallback;
        }

        String request = prompt.toLowerCase();
        boolean social = containsAny(request, "社区", "论坛", "话题", "发帖", "帖子", "评论", "回复",
                "点赞", "收藏", "关注", "通知", "社交");
        boolean commerce = containsAny(request, "商城", "电商", "商品", "购物车", "订单", "下单");
        boolean todo = containsAny(request, "待办", "任务", "todo", "task");
        if (social) {
            fallback.setDataModels(List.of(
                    fallbackModel("topic", "话题", List.of(
                            fallbackField("category", "分类", RuntimeFieldType.STRING, true, 64),
                            fallbackField("title", "标题", RuntimeFieldType.STRING, true, 255),
                            fallbackField("content", "内容", RuntimeFieldType.TEXT, true, 20_000),
                            fallbackField("author_id", "作者 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("tags", "标签", RuntimeFieldType.STRING, false, 500),
                            fallbackField("likes", "点赞数", RuntimeFieldType.INTEGER, false, null),
                            fallbackField("created_at", "发布时间", RuntimeFieldType.DATETIME, true, null)
                    )),
                    fallbackModel("comment", "评论", List.of(
                            fallbackField("topic_id", "话题 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("user_id", "用户 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("content", "评论内容", RuntimeFieldType.TEXT, true, 5000),
                            fallbackField("parent_id", "父评论 ID", RuntimeFieldType.INTEGER, false, null),
                            fallbackField("created_at", "评论时间", RuntimeFieldType.DATETIME, true, null)
                    )),
                    fallbackModel("notification", "通知", List.of(
                            fallbackField("user_id", "接收用户 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("type", "通知类型", RuntimeFieldType.STRING, true, 64),
                            fallbackField("related_id", "关联记录 ID", RuntimeFieldType.INTEGER, false, null),
                            fallbackField("content", "通知内容", RuntimeFieldType.STRING, true, 500),
                            fallbackField("read", "是否已读", RuntimeFieldType.BOOLEAN, true, null),
                            fallbackField("created_at", "通知时间", RuntimeFieldType.DATETIME, true, null)
                    ))
            ));
            fallback.setPages(List.of("话题首页", "话题详情", "通知中心", "个人中心"));
            fallback.setFeatures(List.of("发布话题", "评论回复", "点赞互动", "标签筛选", "消息通知", "数据持久化"));
        } else if (commerce) {
            fallback.setDataModels(List.of(
                    fallbackModel("product", "商品", List.of(
                            fallbackField("name", "商品名称", RuntimeFieldType.STRING, true, 255),
                            fallbackField("description", "商品描述", RuntimeFieldType.TEXT, false, 20_000),
                            fallbackField("price", "价格", RuntimeFieldType.DECIMAL, true, null),
                            fallbackField("stock", "库存", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("image_url", "商品图片", RuntimeFieldType.STRING, false, 2000),
                            fallbackField("status", "商品状态", RuntimeFieldType.STRING, true, 64)
                    )),
                    fallbackModel("order", "订单", List.of(
                            fallbackField("user_id", "用户 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("total_amount", "订单金额", RuntimeFieldType.DECIMAL, true, null),
                            fallbackField("status", "订单状态", RuntimeFieldType.STRING, true, 64),
                            fallbackField("created_at", "下单时间", RuntimeFieldType.DATETIME, true, null)
                    )),
                    fallbackModel("order_item", "订单明细", List.of(
                            fallbackField("order_id", "订单 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("product_id", "商品 ID", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("quantity", "数量", RuntimeFieldType.INTEGER, true, null),
                            fallbackField("unit_price", "成交单价", RuntimeFieldType.DECIMAL, true, null)
                    ))
            ));
            fallback.setPages(List.of("商品列表", "商品详情", "购物车", "订单中心"));
            fallback.setFeatures(List.of("浏览商品", "购物车", "创建订单", "订单状态管理", "库存管理", "数据持久化"));
        } else if (todo) {
            fallback.setDataModels(List.of(fallbackModel("tasks", "任务", List.of(
                    fallbackField("title", "标题", RuntimeFieldType.STRING, true, 200),
                    fallbackField("content", "详细内容", RuntimeFieldType.TEXT, false, 5000),
                    fallbackField("completed", "完成状态", RuntimeFieldType.BOOLEAN, true, null),
                    fallbackField("due_date", "截止日期", RuntimeFieldType.DATE, false, null)
            ))));
            fallback.setPages(List.of("任务列表"));
            fallback.setFeatures(List.of("新增任务", "查看任务", "修改任务", "删除任务", "完成状态管理", "数据持久化"));
        } else {
            fallback.setDataModels(List.of(fallbackModel("records", "业务记录", List.of(
                    fallbackField("title", "标题", RuntimeFieldType.STRING, true, 200),
                    fallbackField("content", "内容", RuntimeFieldType.TEXT, false, 5000),
                    fallbackField("status", "状态", RuntimeFieldType.STRING, false, 64)
            ))));
            fallback.setPages(List.of("数据列表"));
            fallback.setFeatures(List.of("新增数据", "查看数据", "修改数据", "删除数据", "数据持久化"));
        }
        fallback.setTechStack(List.of("vue_project", "平台托管数据 SDK"));
        return fallback;
    }

    private GenerationDataModel fallbackModel(String key, String name, List<GenerationDataField> fields) {
        GenerationDataModel model = new GenerationDataModel();
        model.setModelKey(key);
        model.setDisplayName(name);
        model.setFields(fields);
        model.setPublicOperations(new LinkedHashSet<>());
        return model;
    }

    private GenerationDataField fallbackField(String key, String name, RuntimeFieldType type,
                                               boolean required, Integer maxLength) {
        GenerationDataField field = new GenerationDataField();
        field.setKey(key);
        field.setName(name);
        field.setType(type);
        field.setRequired(required);
        field.setMaxLength(maxLength);
        return field;
    }

    private boolean needsDatabaseFallback(String prompt) {
        String request = StrUtil.blankToDefault(prompt, "").toLowerCase();
        if (containsAny(request, "不需要数据库", "无需数据库", "不要数据库", "纯前端", "纯静态")) {
            return false;
        }
        return containsAny(request, "数据库", "持久化", "保存数据", "数据不能丢", "刷新后", "多人共享",
                "表单提交", "内容管理", "业务记录", "待办", "任务管理", "社区", "论坛", "话题",
                "发帖", "帖子", "评论", "回复", "点赞", "收藏", "关注", "通知", "社交", "商城",
                "电商", "商品", "购物车", "订单", "下单", "预约", "报名", "登录", "注册",
                "database", "persist", "community", "comment", "order", "booking");
    }

    private boolean containsAny(String source, String... candidates) {
        if (source == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BuildCheckVO checkBuild(Long appId, User loginUser) {
        App app = getOwnedApp(appId, loginUser);
        ThrowUtils.throwIf("GENERATING".equals(app.getGenerationStatus()), ErrorCode.OPERATION_ERROR,
                "应用生成中，请等待生成完成后再检查");
        Path root = projectPathGuard.resolveGeneratedRoot(appId, app.getCodeGenType());
        BuildCheckVO result;
        if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())) {
            VueProjectBuilder.BuildResult buildResult = vueProjectBuilder.checkProject(root.toString());
            result = BuildCheckVO.builder()
                    .success(buildResult.success())
                    .stage(buildResult.stage())
                    .output(buildResult.output())
                    .durationMs(buildResult.durationMs())
                    .checkedAt(LocalDateTime.now())
                    .build();
        } else {
            long startedAt = System.currentTimeMillis();
            Path indexFile = root.resolve("index.html");
            boolean success = Files.isRegularFile(indexFile);
            result = BuildCheckVO.builder()
                    .success(success)
                    .stage(success ? "COMPLETED" : "PROJECT")
                    .output(success ? "静态项目结构检查通过：index.html 存在。"
                            : "静态项目结构检查失败：项目根目录缺少 index.html。")
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
        latestBuildChecks.put(appId, result);
        GenerationTask latestTask = generationTaskService.getLatestByAppId(appId);
        if (latestTask != null && "AUTO_REPAIR".equals(latestTask.getTaskType())
                && "SUCCEEDED".equals(latestTask.getStatus())) {
            latestTask.setBuildStatus(Boolean.TRUE.equals(result.getSuccess()) ? "PASSED" : "FAILED");
            String buildOutput = StrUtil.blankToDefault(result.getOutput(), "无构建输出");
            latestTask.setBuildOutput(buildOutput.length() > 60_000
                    ? buildOutput.substring(buildOutput.length() - 60_000) : buildOutput);
            generationTaskService.updateById(latestTask);
        }
        return result;
    }

    @Override
    public Flux<String> repairBuild(Long appId, int attempt, int maxAttempts, User loginUser) {
        App app = getOwnedApp(appId, loginUser);
        ThrowUtils.throwIf(attempt < 1 || maxAttempts < 1 || maxAttempts > 3 || attempt > maxAttempts,
                ErrorCode.PARAMS_ERROR, "自动修复轮次错误");
        ThrowUtils.throwIf("GENERATING".equals(app.getGenerationStatus()), ErrorCode.OPERATION_ERROR,
                "应用正在生成中");
        BuildCheckVO build = latestBuildChecks.get(appId);
        if (build == null) {
            build = checkBuild(appId, loginUser);
        }
        ThrowUtils.throwIf(Boolean.TRUE.equals(build.getSuccess()), ErrorCode.OPERATION_ERROR,
                "当前构建已经通过，无需自动修复");
        ThrowUtils.throwIf("PROJECT".equalsIgnoreCase(build.getStage()) || !hasUsableProject(app),
                ErrorCode.OPERATION_ERROR,
                "项目文件尚未生成，无法执行构建修复，请重新生成应用");
        ThrowUtils.throwIf(!isRepairableBuildStage(build.getStage()), ErrorCode.OPERATION_ERROR,
                "当前构建失败阶段不支持 AI 自动修复，请查看构建日志");
        String rawOutput = StrUtil.blankToDefault(build.getOutput(), "无构建日志");
        String diagnostics = rawOutput.length() > 20_000
                ? rawOutput.substring(rawOutput.length() - 20_000)
                : rawOutput;
        String aiPrompt = """
                修复当前项目的构建错误。先读取 package.json 和构建日志涉及的文件，定位根因后使用文件工具直接修改。
                不要重建整个项目，不要删除无关功能，不要只解释问题。修复完成后检查关联代码的一致性并调用 exit 工具。

                构建失败阶段：%s
                构建日志（可能只保留末尾）：
                <build_output>
                %s
                </build_output>
                """.formatted(build.getStage(), diagnostics);
        return startGeneration(app, aiPrompt, "自动修复构建错误（第 " + attempt + " 轮）",
                "自动修复构建错误（第 " + attempt + " 轮）", loginUser,
                GenerationMode.MODIFY, "AUTO_REPAIR", attempt, maxAttempts);
    }

    private App getOwnedApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR,
                "无权限访问该应用");
        return app;
    }

    private Flux<String> startGeneration(App app, String aiPrompt, String historyMessage,
                                         String versionChangeMessage,
                                         User loginUser, GenerationMode generationMode) {
        return startGeneration(app, aiPrompt, historyMessage, versionChangeMessage, loginUser,
                generationMode, "GENERATION", 0, 0);
    }

    private Flux<String> startGeneration(App app, String aiPrompt, String historyMessage,
                                         String versionChangeMessage, User loginUser,
                                         GenerationMode generationMode, String taskType,
                                         int attempt, int maxAttempts) {
        Long appId = app.getId();
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }

        if (Boolean.TRUE.equals(app.getRagEnabled())) {
            Path currentProjectPath = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, codeGenType + "_" + appId);
            ragKnowledgeService.indexProjectDirectoryIfEmpty(appId, loginUser.getId(), "CODE", currentProjectPath);
            // Retrieve memory before persisting the current message so it cannot retrieve itself.
            aiPrompt = ragKnowledgeService.augmentPrompt(appId, aiPrompt);
        }
        chatHistoryService.addChatMessage(appId, historyMessage,
                ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        AppVersion version = appVersionService.startVersion(appId, codeGenType,
                AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType + "_" + appId,
                versionChangeMessage);
        GenerationTask task = new GenerationTask();
        task.setAppId(appId);
        task.setUserId(loginUser.getId());
        task.setPrompt(versionChangeMessage);
        task.setMode(generationMode.name());
        task.setTaskType(taskType);
        task.setAttempt(attempt);
        task.setMaxAttempts(maxAttempts);
        task.setStatus("GENERATING");
        task.setCurrentStep("AI 生成");
        task.setProgress(5);
        task.setStartedAt(LocalDateTime.now());
        generationTaskService.save(task);
        generationVersions.put(appId, version);
        App generatingApp = new App();
        generatingApp.setId(appId);
        generatingApp.setGenerationStatus("GENERATING");
        updateById(generatingApp);

        // The generation POST and SSE observer are separate requests. Replay recent events so
        // files emitted between them are not lost before EventSource attaches.
        Sinks.Many<String> clientStream = Sinks.many().replay().limit(1_000);
        generationStreams.put(appId, clientStream);
        AtomicInteger processedFiles = new AtomicInteger();
        AtomicInteger processedChunks = new AtomicInteger();
        Set<String> observedFiles = ConcurrentHashMap.newKeySet();
        AtomicLong lastProgressPersistAt = new AtomicLong();

        Flux<String> codeStream;
        try {
            codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                    aiPrompt, codeGenTypeEnum, appId, generationMode);
        } catch (Throwable error) {
            appVersionService.markFailed(version);
            markTaskFailed(task, error.getMessage());
            App failedApp = new App();
            failedApp.setId(appId);
            failedApp.setGenerationStatus("FAILED");
            updateById(failedApp);
            generationVersions.remove(appId);
            generationStreams.remove(appId, clientStream);
            clientStream.tryEmitError(error);
            return clientStream.asFlux();
        }
        Flux<String> executionStream = streamHandlerExecutor
                .doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .timeout(Duration.ofMinutes("AUTO_REPAIR".equals(taskType) ? 8 : 20))
                .doOnNext(chunk -> updateTaskProgress(task, chunk, observedFiles, processedFiles,
                        processedChunks, lastProgressPersistAt))
                .doOnComplete(() -> {
                    task.setCurrentStep("保存版本");
                    task.setProgress(92);
                    generationTaskService.updateById(task);
                    try {
                        validateGeneratedProject(app);
                        snapshotVersion(version, appId, codeGenType);
                        if (Boolean.TRUE.equals(app.getRagEnabled())) {
                            indexGeneratedProject(appId, codeGenType, loginUser.getId());
                        }
                        appVersionService.markReady(version);
                    } catch (Exception e) {
                        log.error("保存应用版本快照失败，appId: {}, version: {}", appId, version.getVersionNumber(), e);
                        appVersionService.markFailed(version);
                        markTaskFailed(task, e.getMessage());
                        App failedApp = new App();
                        failedApp.setId(appId);
                        failedApp.setGenerationStatus("FAILED");
                        updateById(failedApp);
                        return;
                    }
                    App completedApp = new App();
                    completedApp.setId(appId);
                    completedApp.setCurrentVersionId(version.getId());
                    completedApp.setVersionNumber(version.getVersionNumber());
                    completedApp.setGenerationStatus("COMPLETED");
                    updateById(completedApp);
                    task.setStatus("SUCCEEDED");
                    task.setCurrentStep("完成");
                    task.setProgress(100);
                    task.setFinishedAt(LocalDateTime.now());
                    generationTaskService.updateById(task);
                })
                .doOnError(error -> {
                    appVersionService.markFailed(version);
                    markTaskFailed(task, error.getMessage());
                    App failedApp = new App();
                    failedApp.setId(appId);
                    failedApp.setGenerationStatus("FAILED");
                    updateById(failedApp);
                })
                .doFinally(signalType -> {
                    generationSubscriptions.remove(appId);
                    generationVersions.remove(appId);
                    generationStreams.remove(appId, clientStream);
                });
        Disposable execution = executionStream.subscribe(
                chunk -> clientStream.tryEmitNext(chunk),
                clientStream::tryEmitError,
                clientStream::tryEmitComplete);
        generationSubscriptions.put(appId, execution);
        return clientStream.asFlux();
    }

    private void updateTaskProgress(GenerationTask task, String chunk, Set<String> observedFiles,
                                    AtomicInteger processedFiles, AtomicInteger processedChunks,
                                    AtomicLong lastPersistAt) {
        int chunkCount = processedChunks.incrementAndGet();
        String currentFile = null;
        boolean discoveredFile = false;
        try {
            if (StrUtil.isNotBlank(chunk) && chunk.trim().startsWith("{")) {
                var json = JSONUtil.parseObj(chunk);
                String type = json.getStr("type");
                if ("code_file".equals(type) || "code_file_delta".equals(type)) {
                    currentFile = json.getStr("path");
                    if (StrUtil.isNotBlank(currentFile) && observedFiles.add(currentFile)) {
                        processedFiles.incrementAndGet();
                        discoveredFile = true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Plain assistant text is also a valid stream chunk.
        }
        long now = System.currentTimeMillis();
        if (!discoveredFile && now - lastPersistAt.get() < 1_500) return;
        if (currentFile != null) task.setCurrentFile(currentFile);
        task.setCurrentStep(currentFile == null ? "AI 生成" : "正在生成文件");
        task.setProgress(Math.min(88,
                Math.max(10 + processedFiles.get() * 6, 10 + chunkCount / 20)));
        lastPersistAt.set(now);
        generationTaskService.updateById(task);
    }

    @Override
    public void cancelGeneration(Long appId, User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        AppVersion version = generationVersions.remove(appId);
        if (version != null) {
            appVersionService.markCancelled(version);
        }
        GenerationTask task = generationTaskService.getLatestByAppId(appId);
        if (task != null && "GENERATING".equals(task.getStatus())) {
            task.setStatus("CANCELLED");
            task.setCurrentStep("已取消");
            task.setFinishedAt(LocalDateTime.now());
            generationTaskService.updateById(task);
        }
        Disposable subscription = generationSubscriptions.remove(appId);
        if (subscription != null) {
            subscription.dispose();
        }
        Sinks.Many<String> stream = generationStreams.remove(appId);
        if (stream != null) stream.tryEmitComplete();
        App update = new App();
        update.setId(appId);
        update.setGenerationStatus("CANCELLED");
        updateById(update);
    }

    private void markTaskFailed(GenerationTask task, String errorMessage) {
        task.setStatus("FAILED");
        task.setCurrentStep("生成失败");
        String detail = friendlyGenerationError(errorMessage);
        task.setErrorMessage(detail.length() > 1900 ? detail.substring(0, 1900) : detail);
        task.setFinishedAt(LocalDateTime.now());
        generationTaskService.updateById(task);
    }

    /** Persist only actionable, provider-independent text; never store API response bodies. */
    private String friendlyGenerationError(String errorMessage) {
        String detail = StrUtil.blankToDefault(errorMessage, "生成过程中发生未知错误");
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
        if (detail.startsWith("{") || detail.contains("request_id") || detail.contains("chatcmpl-")) {
            return "AI 生成服务暂时不可用，请稍后重试。";
        }
        return detail;
    }

    private boolean hasUsableProject(App app) {
        Path root = projectPathGuard.resolveGeneratedRoot(app.getId(), app.getCodeGenType());
        if (!Files.isDirectory(root)) return false;
        return CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())
                ? Files.isRegularFile(root.resolve("package.json"))
                : Files.isRegularFile(root.resolve("index.html"));
    }

    private boolean isRepairableBuildStage(String stage) {
        return "DEPENDENCIES".equalsIgnoreCase(stage)
                || "BUILD".equalsIgnoreCase(stage)
                || "OUTPUT".equalsIgnoreCase(stage);
    }

    private void validateGeneratedProject(App app) {
        ThrowUtils.throwIf(!hasUsableProject(app), ErrorCode.OPERATION_ERROR,
                CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())
                        ? "AI 未生成完整项目：缺少 package.json"
                        : "AI 未生成完整项目：缺少 index.html");
    }

    @Override
    public void saveCodeFile(AppCodeFileUpdateRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getAppId() == null
                        || StrUtil.isBlank(request.getFilePath()) || request.getContent() == null,
                ErrorCode.PARAMS_ERROR, "文件参数不完整");
        App app = getById(request.getAppId());
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        String relativePath = request.getFilePath().replace('\\', '/');
        ThrowUtils.throwIf(projectPathGuard.isPlatformManagedFile(relativePath), ErrorCode.NO_AUTH_ERROR,
                "该文件由平台管理，不允许手动修改");
        Path root = projectPathGuard.resolveGeneratedRoot(request.getAppId(), app.getCodeGenType());
        Path target = projectPathGuard.resolveGeneratedPath(request.getAppId(), app.getCodeGenType(), relativePath);
        if (request.getContent().getBytes(StandardCharsets.UTF_8).length > ProjectPathGuard.MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小超过 5 MB 限制");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, request.getContent());
            if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())) {
                boolean built = vueProjectBuilder.buildProject(root.toString());
                ThrowUtils.throwIf(!built, ErrorCode.SYSTEM_ERROR, "项目构建失败，文件已保存但预览未更新");
            }
        } catch (Exception e) {
            log.error("保存应用文件失败, appId: {}, path: {}", request.getAppId(), relativePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
    }

    @Override
    public List<CodeFileMessage> listCodeFiles(Long appId, User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        Path root = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR,
                app.getCodeGenType() + "_" + appId).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return List.of();
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains(File.separator + "node_modules" + File.separator))
                    .filter(path -> !path.toString().contains(File.separator + "dist" + File.separator))
                    .filter(path -> isCodeFile(path.getFileName().toString()))
                    .filter(path -> {
                        try { return Files.size(path) <= 512 * 1024; } catch (Exception e) { return false; }
                    })
                    .map(path -> {
                        try {
                            String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            return new CodeFileMessage(relative, content, detectLanguage(relative), "existing", null);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("读取应用文件列表失败, appId: {}", appId, e);
            return List.of();
        }
    }

    private String detectLanguage(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".vue")) return "vue";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".tsx")) return "tsx";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".jsx")) return "jsx";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".json")) return "json";
        return "text";
    }

    private boolean isCodeFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".vue") || lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".css")
                || lower.endsWith(".html") || lower.endsWith(".json") || lower.endsWith(".md")
                || lower.endsWith(".txt") || lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    private void snapshotVersion(AppVersion version, Long appId, String codeGenType) {
        String sourcePath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType + "_" + appId;
        File sourceDir = new File(sourcePath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.SYSTEM_ERROR, "代码生成目录不存在，无法保存版本");
        String targetPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator
                + "code_versions" + File.separator + appId + File.separator + "v" + version.getVersionNumber();
        File targetDir = new File(targetPath);
        FileUtil.del(targetDir);
        FileUtil.copyContent(sourceDir, targetDir, true);
        version.setSourcePath(targetPath);
        appVersionService.updateById(version);
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        String appName;
        try {
            appName = aiCodeGenTypeRoutingServiceFactory.createAiAppNameService().generateName(initPrompt);
            appName = StrUtil.trim(appName).replaceAll("[\\r\\n\\\"'。，、；：:]+$", "");
        } catch (Exception e) {
            log.warn("AI 应用名称生成失败，使用默认名称: {}", e.getMessage());
            appName = null;
        }
        if (StrUtil.isBlank(appName)) {
            appName = initPrompt.replaceAll("\\s+", " ").trim();
        }
        app.setAppName(appName.substring(0, Math.min(appName.length(), 20)));
        app.setVersionNumber(0);
        app.setGenerationStatus("IDLE");
        app.setDeployStatus("NOT_DEPLOYED");
        app.setRagEnabled(Boolean.TRUE.equals(appAddRequest.getRagEnabled()));
        // AI 路由是辅助能力，模型不可用时不能阻断应用创建。
        CodeGenTypeEnum selectedCodeGenType = selectCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    CodeGenTypeEnum selectCodeGenType(String prompt) {
        try {
            AiCodeGenTypeRoutingService routingService =
                    aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
            CodeGenTypeEnum selected = routingService.routeCodeGenType(prompt);
            if (selected != null) {
                return selected;
            }
            log.warn("AI 代码生成类型路由返回空值，默认使用 Vue 工程模式");
        } catch (Exception e) {
            log.warn("AI 代码生成类型路由失败，默认使用 Vue 工程模式: {}", e.getMessage());
        }
        return CodeGenTypeEnum.VUE_PROJECT;
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，获取原始代码生成路径（应用访问目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 构建完成后，需要将构建后的文件复制到部署目录
            sourceDir = distDir;
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 9. 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        updateApp.setDeployStatus("RUNNING");
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 构建应用访问URL
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        // 11. 异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    @Override
    public String startDeployment(Long appId, User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(app.getDeployKey()), ErrorCode.NOT_FOUND_ERROR, "应用尚未部署");
        App update = new App();
        update.setId(appId);
        update.setDeployStatus("RUNNING");
        updateById(update);
        return String.format("%s/%s/", deployHost, app.getDeployKey());
    }

    @Override
    public void stopDeployment(Long appId, User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        App update = new App();
        update.setId(appId);
        update.setDeployStatus("PAUSED");
        updateById(update);
    }

    @Override
    public synchronized void rollbackVersion(Long appId, Long versionId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0 || versionId == null || versionId <= 0,
                ErrorCode.PARAMS_ERROR, "应用或版本 ID 无效");
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权回滚该应用");

        AppVersion version = appVersionService.getById(versionId);
        ThrowUtils.throwIf(version == null || !appId.equals(version.getAppId()),
                ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        ThrowUtils.throwIf(!"READY".equals(version.getStatus()), ErrorCode.PARAMS_ERROR,
                "只有已完成的版本可以回滚");
        ThrowUtils.throwIf(StrUtil.isBlank(version.getSourcePath()), ErrorCode.SYSTEM_ERROR,
                "版本快照不存在");

        try {
            File versionRoot = new File(System.getProperty("user.dir") + File.separator + "tmp"
                    + File.separator + "code_versions" + File.separator + appId).getCanonicalFile();
            File snapshotDir = new File(version.getSourcePath()).getCanonicalFile();
            ThrowUtils.throwIf(!snapshotDir.toPath().startsWith(versionRoot.toPath())
                            || !snapshotDir.isDirectory(),
                    ErrorCode.PARAMS_ERROR, "版本快照路径无效");

            File outputDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                    + app.getCodeGenType() + "_" + appId).getCanonicalFile();
            FileUtil.del(outputDir);
            FileUtil.copyContent(snapshotDir, outputDir, true);

            if (StrUtil.isNotBlank(app.getDeployKey())) {
                syncDeploymentFiles(app, outputDir);
            }

            App update = new App();
            update.setId(appId);
            update.setCurrentVersionId(version.getId());
            update.setVersionNumber(version.getVersionNumber());
            update.setGenerationStatus("COMPLETED");
            updateById(update);

            if ("RUNNING".equals(app.getDeployStatus())) {
                generateAppScreenshotAsync(appId, String.format("%s/%s/", deployHost, app.getDeployKey()));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("应用版本回滚失败，appId: {}, versionId: {}", appId, versionId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本回滚失败，请稍后重试");
        }
    }

    /** 将当前生成目录同步到部署目录，Vue 项目会先重新构建 dist。 */
    @Override
    public List<CodeFileDiffVO> diffVersion(Long appId, Long versionId, User loginUser) {
        return diffVersions(appId, versionId, null, loginUser);
    }

    @Override
    public List<CodeFileDiffVO> diffVersions(Long appId, Long baseVersionId, Long targetVersionId,
                                             User loginUser) {
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        Path oldRoot = resolveDiffRoot(appId, baseVersionId);
        Path currentRoot = targetVersionId == null
                ? projectPathGuard.resolveGeneratedRoot(appId, app.getCodeGenType())
                : resolveDiffRoot(appId, targetVersionId);
        ThrowUtils.throwIf(!Files.isDirectory(oldRoot) || !Files.isDirectory(currentRoot),
                ErrorCode.NOT_FOUND_ERROR, "版本文件不存在");
        Set<String> paths = new LinkedHashSet<>();
        collectRelativeFiles(oldRoot, paths);
        collectRelativeFiles(currentRoot, paths);
        List<CodeFileDiffVO> result = new ArrayList<>();
        for (String relative : paths) {
            Path before = oldRoot.resolve(relative).normalize();
            Path after = currentRoot.resolve(relative).normalize();
            boolean beforeExists = Files.isRegularFile(before);
            boolean afterExists = Files.isRegularFile(after);
            String beforeText = beforeExists ? readDiffContent(before) : "";
            String afterText = afterExists ? readDiffContent(after) : "";
            String status = !beforeExists ? "ADDED" : !afterExists ? "DELETED"
                    : beforeText.equals(afterText) ? "UNCHANGED" : "MODIFIED";
            if ("UNCHANGED".equals(status)) continue;
            CodeFileDiffVO diff = new CodeFileDiffVO();
            diff.setPath(relative);
            diff.setStatus(status);
            diff.setBeforeContent(beforeText);
            diff.setAfterContent(afterText);
            populateLineDiff(diff, relative, beforeText, afterText);
            result.add(diff);
        }
        return result;
    }

    private Path resolveDiffRoot(Long appId, Long versionId) {
        AppVersion version = appVersionService.getById(versionId);
        ThrowUtils.throwIf(version == null || !appId.equals(version.getAppId())
                        || StrUtil.isBlank(version.getSourcePath()),
                ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        return Path.of(version.getSourcePath()).toAbsolutePath().normalize();
    }

    private void populateLineDiff(CodeFileDiffVO diff, String path, String beforeText, String afterText) {
        List<String> beforeLines = splitLines(beforeText);
        List<String> afterLines = splitLines(afterText);
        Patch<String> patch = DiffUtils.diff(beforeLines, afterLines);
        int additions = patch.getDeltas().stream().mapToInt(delta -> delta.getTarget().size()).sum();
        int deletions = patch.getDeltas().stream().mapToInt(delta -> delta.getSource().size()).sum();
        diff.setAdditions(additions);
        diff.setDeletions(deletions);
        diff.setUnifiedDiff(String.join("\n",
                UnifiedDiffUtils.generateUnifiedDiff("a/" + path, "b/" + path, beforeLines, patch, 3)));
        diff.setLines(buildDiffRows(beforeLines, afterLines, patch));
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(List.of(content.split("\\R", -1)));
    }

    private List<CodeDiffLineVO> buildDiffRows(List<String> before, List<String> after, Patch<String> patch) {
        List<CodeDiffLineVO> rows = new ArrayList<>();
        int beforeCursor = 0;
        int afterCursor = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int sourcePosition = delta.getSource().getPosition();
            int targetPosition = delta.getTarget().getPosition();
            while (beforeCursor < sourcePosition && afterCursor < targetPosition) {
                rows.add(new CodeDiffLineVO("CONTEXT", beforeCursor + 1, afterCursor + 1,
                        before.get(beforeCursor++), after.get(afterCursor++)));
            }
            List<String> sourceLines = delta.getSource().getLines();
            List<String> targetLines = delta.getTarget().getLines();
            int changedRows = Math.max(sourceLines.size(), targetLines.size());
            for (int i = 0; i < changedRows; i++) {
                boolean hasBefore = i < sourceLines.size();
                boolean hasAfter = i < targetLines.size();
                String type = hasBefore && hasAfter ? "MODIFIED" : hasBefore ? "DELETED" : "ADDED";
                rows.add(new CodeDiffLineVO(type,
                        hasBefore ? sourcePosition + i + 1 : null,
                        hasAfter ? targetPosition + i + 1 : null,
                        hasBefore ? sourceLines.get(i) : null,
                        hasAfter ? targetLines.get(i) : null));
            }
            beforeCursor = sourcePosition + sourceLines.size();
            afterCursor = targetPosition + targetLines.size();
        }
        while (beforeCursor < before.size() && afterCursor < after.size()) {
            rows.add(new CodeDiffLineVO("CONTEXT", beforeCursor + 1, afterCursor + 1,
                    before.get(beforeCursor++), after.get(afterCursor++)));
        }
        return compactContextRows(rows);
    }

    private List<CodeDiffLineVO> compactContextRows(List<CodeDiffLineVO> rows) {
        List<CodeDiffLineVO> compact = new ArrayList<>();
        int index = 0;
        while (index < rows.size()) {
            if (!"CONTEXT".equals(rows.get(index).getType())) {
                compact.add(rows.get(index++));
                continue;
            }
            int end = index;
            while (end < rows.size() && "CONTEXT".equals(rows.get(end).getType())) end++;
            int length = end - index;
            if (length <= 7) {
                compact.addAll(rows.subList(index, end));
            } else {
                compact.addAll(rows.subList(index, index + 3));
                compact.add(new CodeDiffLineVO("HUNK", null, null, "...", "..."));
                compact.addAll(rows.subList(end - 3, end));
            }
            index = end;
        }
        return compact;
    }

    private void collectRelativeFiles(Path root, Set<String> result) {
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).limit(ProjectPathGuard.MAX_PROJECT_FILES)
                    .forEach(path -> result.add(root.relativize(path).toString().replace(File.separatorChar, '/')));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取版本文件失败");
        }
    }

    private String readDiffContent(Path path) {
        try {
            if (Files.size(path) > ProjectPathGuard.MAX_FILE_BYTES) return "[二进制或超大文件]";
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[文件不可读取]";
        }
    }

    @Override
    public void restoreVersionFile(AppVersionFileRestoreRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getAppId() == null || request.getVersionId() == null
                        || StrUtil.isBlank(request.getFilePath()),
                ErrorCode.PARAMS_ERROR, "恢复文件参数不完整");
        App app = getOwnedApp(request.getAppId(), loginUser);
        Path sourceRoot = resolveDiffRoot(app.getId(), request.getVersionId());
        Path targetRoot = projectPathGuard.resolveGeneratedRoot(app.getId(), app.getCodeGenType());
        String relativePath = request.getFilePath().replace('\\', '/');
        Path target = projectPathGuard.resolveGeneratedPath(app.getId(), app.getCodeGenType(), relativePath);
        Path source = sourceRoot.resolve(relativePath).normalize();
        ThrowUtils.throwIf(!source.startsWith(sourceRoot), ErrorCode.PARAMS_ERROR, "文件路径错误");
        byte[] backup = null;
        boolean targetExisted = Files.isRegularFile(target);
        try {
            if (targetExisted) backup = Files.readAllBytes(target);
            if (Files.isRegularFile(source)) {
                projectPathGuard.validateFileSize(source);
                Files.createDirectories(target.getParent());
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(target);
            }
            if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())) {
                VueProjectBuilder.BuildResult build = vueProjectBuilder.checkProject(targetRoot.toString());
                if (!build.success()) throw new IllegalStateException("恢复后构建失败：" + build.output());
            }
            AppVersion restoredVersion = appVersionService.startVersion(app.getId(), app.getCodeGenType(),
                    targetRoot.toString(), "恢复文件 " + relativePath);
            snapshotVersion(restoredVersion, app.getId(), app.getCodeGenType());
            appVersionService.markReady(restoredVersion);
            App update = new App();
            update.setId(app.getId());
            update.setCurrentVersionId(restoredVersion.getId());
            update.setVersionNumber(restoredVersion.getVersionNumber());
            updateById(update);
        } catch (Exception e) {
            try {
                if (targetExisted && backup != null) Files.write(target, backup);
                else Files.deleteIfExists(target);
            } catch (Exception rollbackError) {
                log.error("恢复文件失败后回退工作区也失败, appId: {}, path: {}",
                        app.getId(), relativePath, rollbackError);
            }
            log.error("恢复版本文件失败, appId: {}, path: {}", app.getId(), relativePath, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "恢复文件失败：" + e.getMessage());
        }
    }

    private void syncDeploymentFiles(App app, File outputDir) {
        File deploySource = outputDir;
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean buildSuccess = vueProjectBuilder.buildProject(outputDir.getAbsolutePath());
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "版本构建失败，无法同步部署文件");
            deploySource = new File(outputDir, "dist");
            ThrowUtils.throwIf(!deploySource.isDirectory(), ErrorCode.SYSTEM_ERROR,
                    "版本构建完成但未找到 dist 目录");
        }
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey());
        FileUtil.del(deployDir);
        FileUtil.copyContent(deploySource, deployDir, true);
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            try {
                // 调用截图服务生成截图并上传
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                // 更新数据库的封面
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
                log.info("应用封面生成成功，appId: {}, cover: {}", appId, screenshotUrl);
            } catch (Exception e) {
                // 异步任务不能影响部署接口，但必须记录失败原因便于重试
                log.error("应用封面生成失败，appId: {}, url: {}", appId, appUrl, e);
            }
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "good_app_page", allEntries = true)
    public int deleteApps(List<Long> appIds, User loginUser) {
        ThrowUtils.throwIf(CollUtil.isEmpty(appIds), ErrorCode.PARAMS_ERROR, "请选择要删除的应用");
        List<Long> uniqueIds = appIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        ThrowUtils.throwIf(uniqueIds.isEmpty() || uniqueIds.size() > 50,
                ErrorCode.PARAMS_ERROR, "单次最多删除 50 个应用");

        List<App> apps = listByIds(uniqueIds);
        ThrowUtils.throwIf(apps.size() != uniqueIds.size(), ErrorCode.NOT_FOUND_ERROR, "部分应用不存在");
        ThrowUtils.throwIf(apps.stream().anyMatch(app -> !app.getUserId().equals(loginUser.getId())),
                ErrorCode.NO_AUTH_ERROR, "只能删除自己的应用");

        int deleted = 0;
        for (Long appId : uniqueIds) {
            if (removeById(appId)) deleted++;
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "good_app_page", allEntries = true)
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        App app = getById(appId);
        if (app == null) return false;

        Disposable activeSubscription = generationSubscriptions.remove(appId);
        if (activeSubscription != null) activeSubscription.dispose();
        Sinks.Many<String> activeStream = generationStreams.remove(appId);
        if (activeStream != null) activeStream.tryEmitComplete();
        generationVersions.remove(appId);

        chatHistoryService.deleteByAppId(appId);
        appVersionService.deleteByAppId(appId);
        runtimeDataService.deleteByAppId(appId);
        ragKnowledgeService.deleteByAppId(appId);

        try {
            redisChatMemoryStore.deleteMessages(appId);
        } catch (Exception e) {
            log.warn("清理 AI 对话记忆失败, appId: {}", appId, e);
        }
        aiCodeGeneratorServiceFactory.evict(appId);

        FileUtil.del(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + app.getCodeGenType() + "_" + appId);
        FileUtil.del(System.getProperty("user.dir") + File.separator + "tmp" + File.separator
                + "code_versions" + File.separator + appId);
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            FileUtil.del(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey());
        }
        if (StrUtil.isNotBlank(app.getCover())) {
            try {
                cosManager.deleteByUrl(app.getCover());
            } catch (Exception e) {
                log.warn("清理应用封面失败，appId: {}", appId, e);
            }
        }

        return appMapper.deletePermanentlyById(appId) > 0;
    }

    private void indexGeneratedProject(Long appId, String codeGenType, Long userId) {
        try {
            Path projectPath = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, codeGenType + "_" + appId);
            ragKnowledgeService.indexProjectDirectory(appId, userId, "CODE", projectPath);
        } catch (Exception e) {
            // Indexing is best-effort and must not turn successful generation into a failure.
            log.warn("RAG 代码索引失败, appId: {}", appId, e);
        }
    }
}
