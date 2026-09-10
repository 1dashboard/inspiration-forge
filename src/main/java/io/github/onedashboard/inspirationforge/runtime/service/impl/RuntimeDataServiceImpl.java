package io.github.onedashboard.inspirationforge.runtime.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeModelMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeRecordMapper;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelPublishRequest;
import io.github.onedashboard.inspirationforge.runtime.model.auth.RuntimeUserPrincipal;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeApp;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeModel;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeRecord;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeEnvironment;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeConfigVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeModelVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimePageVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeRecordVO;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeDataService;
import io.github.onedashboard.inspirationforge.runtime.validation.RuntimeSchemaValidator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RuntimeDataServiceImpl implements RuntimeDataService {

    @Resource
    private RuntimeAppMapper runtimeAppMapper;

    @Resource
    private RuntimeModelMapper runtimeModelMapper;

    @Resource
    private RuntimeRecordMapper runtimeRecordMapper;

    @Resource
    private AppMapper appMapper;

    @Resource
    private RuntimeSchemaValidator schemaValidator;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return;
        }
        runtimeRecordMapper.deletePermanentlyByAppId(appId);
        runtimeModelMapper.deletePermanentlyByAppId(appId);
        runtimeAppMapper.deletePermanentlyByAppId(appId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuntimeConfigVO ensureConfig(Long appId, User platformUser) {
        requireOwner(appId, platformUser);
        RuntimeApp runtimeApp = findRuntimeAppByAppId(appId);
        if (runtimeApp == null) {
            LocalDateTime now = LocalDateTime.now();
            runtimeApp = new RuntimeApp();
            runtimeApp.setAppId(appId);
            runtimeApp.setRuntimeKey(generateRuntimeKey());
            runtimeApp.setEnabled(true);
            runtimeApp.setCreateTime(now);
            runtimeApp.setUpdateTime(now);
            if (runtimeAppMapper.insert(runtimeApp) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "初始化应用运行面失败");
            }
        }
        return toConfigVO(runtimeApp);
    }

    @Override
    public RuntimeConfigVO getConfig(Long appId, User platformUser) {
        requireOwner(appId, platformUser);
        RuntimeApp runtimeApp = findRuntimeAppByAppId(appId);
        if (runtimeApp == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用尚未启用托管数据");
        }
        return toConfigVO(runtimeApp);
    }

    @Override
    public List<RuntimeModelVO> listModels(Long appId, RuntimeEnvironment environment, User platformUser) {
        requireOwner(appId, platformUser);
        return runtimeModelMapper.selectListByQuery(modelScope(appId, environment)
                        .orderBy("modelKey", true)).stream()
                .map(this::toModelVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuntimeModelVO upsertPreviewModel(RuntimeModelUpsertRequest request, User platformUser) {
        if (request == null || request.getAppId() == null) throw params("应用 ID 不能为空");
        ensureConfig(request.getAppId(), platformUser);
        RuntimeModelDefinition definition = schemaValidator.normalizeDefinition(request.getDefinition());
        String schemaJson = writeJson(definition);
        RuntimeModel model = findModel(request.getAppId(), RuntimeEnvironment.PREVIEW, definition.getModelKey());
        if (model == null) {
            LocalDateTime now = LocalDateTime.now();
            model = new RuntimeModel();
            model.setAppId(request.getAppId());
            model.setEnvironment(RuntimeEnvironment.PREVIEW.name());
            model.setModelKey(definition.getModelKey());
            model.setDisplayName(definition.getDisplayName());
            model.setSchemaJson(schemaJson);
            model.setSchemaVersion(1);
            model.setStatus("DRAFT");
            model.setCreateTime(now);
            model.setUpdateTime(now);
            model.setIsDelete(0);
            if (runtimeModelMapper.insert(model) != 1) throw operation("保存数据模型失败");
        } else {
            model.setDisplayName(definition.getDisplayName());
            model.setSchemaJson(schemaJson);
            model.setSchemaVersion(model.getSchemaVersion() + 1);
            model.setStatus("DRAFT");
            model.setUpdateTime(LocalDateTime.now());
            if (runtimeModelMapper.update(model) != 1) throw operation("更新数据模型失败");
        }
        return toModelVO(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuntimeModelVO publishModel(RuntimeModelPublishRequest request, User platformUser) {
        if (request == null || request.getAppId() == null) throw params("发布参数不完整");
        requireOwner(request.getAppId(), platformUser);
        String modelKey = normalizeModelKey(request.getModelKey());
        RuntimeModel preview = requireModel(request.getAppId(), RuntimeEnvironment.PREVIEW, modelKey);
        RuntimeModel production = findModel(request.getAppId(), RuntimeEnvironment.PRODUCTION, modelKey);
        if (production == null) {
            LocalDateTime now = LocalDateTime.now();
            production = new RuntimeModel();
            production.setAppId(request.getAppId());
            production.setEnvironment(RuntimeEnvironment.PRODUCTION.name());
            production.setModelKey(modelKey);
            production.setSchemaVersion(1);
            production.setCreateTime(now);
            production.setUpdateTime(now);
            production.setIsDelete(0);
        } else {
            production.setSchemaVersion(production.getSchemaVersion() + 1);
            production.setUpdateTime(LocalDateTime.now());
        }
        production.setDisplayName(preview.getDisplayName());
        production.setSchemaJson(preview.getSchemaJson());
        production.setStatus("PUBLISHED");
        int changed = production.getId() == null
                ? runtimeModelMapper.insert(production)
                : runtimeModelMapper.update(production);
        if (changed != 1) throw operation("发布数据模型失败");
        return toModelVO(production);
    }

    @Override
    public RuntimeRecordVO createRecord(Long appId, RuntimeEnvironment environment, String modelKey,
                                        Map<String, Object> data, User platformUser) {
        requireOwner(appId, platformUser);
        return createRecordWithModel(requireModel(appId, environment, normalizeModelKey(modelKey)), data,
                platformUser == null ? null : platformUser.getId(), null);
    }

    @Override
    public RuntimeRecordVO updateRecord(Long appId, RuntimeEnvironment environment, String modelKey, Long recordId,
                                        Integer expectedVersion, Map<String, Object> patch, User platformUser) {
        requireOwner(appId, platformUser);
        RuntimeModel model = requireModel(appId, environment, normalizeModelKey(modelKey));
        return updateRecordWithModel(model, recordId, expectedVersion, patch);
    }

    @Override
    public void deleteRecord(Long appId, RuntimeEnvironment environment, String modelKey, Long recordId,
                             Integer expectedVersion, User platformUser) {
        requireOwner(appId, platformUser);
        RuntimeModel model = requireModel(appId, environment, normalizeModelKey(modelKey));
        deleteRecordWithModel(model, recordId, expectedVersion);
    }

    @Override
    public RuntimeRecordVO getRecord(Long appId, RuntimeEnvironment environment, String modelKey, Long recordId,
                                     User platformUser) {
        requireOwner(appId, platformUser);
        RuntimeModel model = requireModel(appId, environment, normalizeModelKey(modelKey));
        return toRecordVO(requireRecord(model, recordId));
    }

    @Override
    public RuntimePageVO<RuntimeRecordVO> queryRecords(Long appId, RuntimeEnvironment environment, String modelKey,
                                                       int pageNum, int pageSize, User platformUser) {
        requireOwner(appId, platformUser);
        RuntimeModel model = requireModel(appId, environment, normalizeModelKey(modelKey));
        return queryRecordsWithModel(model, pageNum, pageSize);
    }

    @Override
    public List<RuntimeModelVO> listAccessibleModels(String runtimeKey, RuntimeEnvironment environment,
                                                     User platformUser, RuntimeUserPrincipal runtimeUser) {
        RuntimeApp runtimeApp = requireRuntimeApp(runtimeKey);
        boolean owner = isOwner(runtimeApp.getAppId(), platformUser);
        if (environment == RuntimeEnvironment.PREVIEW && !owner) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "预览数据仅应用所有者可访问");
        }
        List<RuntimeModel> models = runtimeModelMapper.selectListByQuery(modelScope(runtimeApp.getAppId(), environment)
                .orderBy("modelKey", true));
        return models.stream()
                .filter(model -> owner || runtimeUser != null || isPubliclyAccessible(model, RuntimeOperation.READ))
                .map(this::toModelVO)
                .toList();
    }

    @Override
    public RuntimeModelDefinition getAccessibleModel(String runtimeKey, RuntimeEnvironment environment,
                                                     String modelKey, RuntimeOperation operation, User platformUser,
                                                     RuntimeUserPrincipal runtimeUser) {
        return readDefinition(requireAccessibleModel(runtimeKey, environment, modelKey, operation, platformUser, runtimeUser));
    }

    @Override
    public RuntimeRecordVO createAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                                  Map<String, Object> data, User platformUser, RuntimeUserPrincipal runtimeUser) {
        RuntimeModel model = requireAccessibleModel(runtimeKey, environment, modelKey,
                RuntimeOperation.CREATE, platformUser, runtimeUser);
        return createRecordWithModel(model, data, platformUser == null ? null : platformUser.getId(),
                runtimeUser == null ? null : runtimeUser.getId());
    }

    @Override
    public RuntimeRecordVO updateAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                                  Long recordId, Integer expectedVersion, Map<String, Object> patch,
                                                  User platformUser, RuntimeUserPrincipal runtimeUser) {
        RuntimeModel model = requireAccessibleModel(runtimeKey, environment, modelKey,
                RuntimeOperation.UPDATE, platformUser, runtimeUser);
        return updateRecordWithModel(model, recordId, expectedVersion, patch);
    }

    @Override
    public void deleteAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                       Long recordId, Integer expectedVersion, User platformUser, RuntimeUserPrincipal runtimeUser) {
        RuntimeModel model = requireAccessibleModel(runtimeKey, environment, modelKey,
                RuntimeOperation.DELETE, platformUser, runtimeUser);
        deleteRecordWithModel(model, recordId, expectedVersion);
    }

    @Override
    public RuntimeRecordVO getAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                               Long recordId, User platformUser, RuntimeUserPrincipal runtimeUser) {
        RuntimeModel model = requireAccessibleModel(runtimeKey, environment, modelKey,
                RuntimeOperation.READ, platformUser, runtimeUser);
        return toRecordVO(requireRecord(model, recordId));
    }

    @Override
    public RuntimePageVO<RuntimeRecordVO> queryAccessibleRecords(String runtimeKey,
                                                                 RuntimeEnvironment environment,
                                                                 String modelKey, int pageNum, int pageSize,
                                                                 User platformUser, RuntimeUserPrincipal runtimeUser) {
        RuntimeModel model = requireAccessibleModel(runtimeKey, environment, modelKey,
                RuntimeOperation.READ, platformUser, runtimeUser);
        return queryRecordsWithModel(model, pageNum, pageSize);
    }

    private RuntimeRecordVO createRecordWithModel(RuntimeModel model, Map<String, Object> data, Long creatorId,
                                                  Long runtimeCreatorId) {
        RuntimeModelDefinition definition = readDefinition(model);
        String dataJson = validatedDataJson(definition, data);
        RuntimeRecord record = new RuntimeRecord();
        record.setAppId(model.getAppId());
        record.setEnvironment(model.getEnvironment());
        record.setModelKey(model.getModelKey());
        record.setDataJson(dataJson);
        record.setRecordVersion(1);
        record.setCreatedByPlatformUserId(creatorId);
        record.setCreatedByRuntimeUserId(runtimeCreatorId);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setIsDelete(0);
        if (runtimeRecordMapper.insert(record) != 1) throw operation("创建数据记录失败");
        return toRecordVO(record);
    }

    private RuntimeRecordVO updateRecordWithModel(RuntimeModel model, Long recordId, Integer expectedVersion,
                                                  Map<String, Object> patch) {
        if (recordId == null || recordId <= 0 || expectedVersion == null || expectedVersion <= 0) {
            throw params("记录 ID 和期望版本不能为空");
        }
        RuntimeRecord current = requireRecord(model, recordId);
        if (!expectedVersion.equals(current.getRecordVersion())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据已被其他操作修改，请刷新后重试");
        }
        Map<String, Object> merged = new LinkedHashMap<>(readData(current.getDataJson()));
        if (patch != null) merged.putAll(patch);
        RuntimeRecord update = new RuntimeRecord();
        update.setDataJson(validatedDataJson(readDefinition(model), merged));
        update.setRecordVersion(expectedVersion + 1);
        update.setUpdateTime(LocalDateTime.now());
        int changed = runtimeRecordMapper.updateByQuery(update, recordScope(model)
                .eq("id", recordId)
                .eq("recordVersion", expectedVersion));
        if (changed != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据版本冲突，请刷新后重试");
        current.setDataJson(update.getDataJson());
        current.setRecordVersion(update.getRecordVersion());
        current.setUpdateTime(LocalDateTime.now());
        return toRecordVO(current);
    }

    private void deleteRecordWithModel(RuntimeModel model, Long recordId, Integer expectedVersion) {
        if (recordId == null || recordId <= 0 || expectedVersion == null || expectedVersion <= 0) {
            throw params("记录 ID 和期望版本不能为空");
        }
        RuntimeRecord update = new RuntimeRecord();
        update.setIsDelete(1);
        update.setUpdateTime(LocalDateTime.now());
        int changed = runtimeRecordMapper.updateByQuery(update, recordScope(model)
                .eq("id", recordId)
                .eq("recordVersion", expectedVersion));
        if (changed != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "记录不存在或版本已变化");
    }

    private RuntimePageVO<RuntimeRecordVO> queryRecordsWithModel(RuntimeModel model, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        Page<RuntimeRecord> page = runtimeRecordMapper.paginate(safePage, safeSize,
                recordScope(model).orderBy("createTime", false));
        return RuntimePageVO.<RuntimeRecordVO>builder()
                .records(page.getRecords().stream().map(this::toRecordVO).toList())
                .total(page.getTotalRow())
                .pageNum(safePage)
                .pageSize(safeSize)
                .build();
    }

    private RuntimeModel requireAccessibleModel(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                                RuntimeOperation operation, User platformUser,
                                                RuntimeUserPrincipal runtimeUser) {
        RuntimeApp runtimeApp = requireRuntimeApp(runtimeKey);
        RuntimeModel model = requireModel(runtimeApp.getAppId(), environment, normalizeModelKey(modelKey));
        if (isOwner(runtimeApp.getAppId(), platformUser)) return model;
        if (environment != RuntimeEnvironment.PRODUCTION && runtimeUser == null)
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "Preview data requires an app user");
        if (runtimeUser == null && !isPubliclyAccessible(model, operation))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "Operation is not public");
        if (runtimeUser != null && !isRoleAllowed(model, runtimeUser, operation))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "User has no permission for this operation");
        return model;
    }

    private boolean isRoleAllowed(RuntimeModel model, RuntimeUserPrincipal runtimeUser, RuntimeOperation operation) {
        if (runtimeUser.getRoles().contains("admin")) return true;
        Map<String, java.util.Set<RuntimeOperation>> permissions = readDefinition(model).getRolePermissions();
        if (permissions != null && !permissions.isEmpty()) {
            return runtimeUser.getRoles().stream().anyMatch(role -> permissions.getOrDefault(role, java.util.Set.of()).contains(operation));
        }
        if (operation == RuntimeOperation.READ) return true;
        return runtimeUser.getRoles().contains("editor") && operation != RuntimeOperation.DELETE;
    }

    private boolean isPubliclyAccessible(RuntimeModel model, RuntimeOperation operation) {
        return "PUBLISHED".equals(model.getStatus())
                && readDefinition(model).getPublicOperations().contains(operation);
    }

    private RuntimeRecord requireRecord(RuntimeModel model, Long recordId) {
        if (recordId == null || recordId <= 0) throw params("记录 ID 无效");
        RuntimeRecord record = runtimeRecordMapper.selectOneByQuery(recordScope(model).eq("id", recordId));
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "数据记录不存在");
        return record;
    }

    private RuntimeModel requireModel(Long appId, RuntimeEnvironment environment, String modelKey) {
        RuntimeModel model = findModel(appId, environment, modelKey);
        if (model == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "数据模型不存在");
        return model;
    }

    private RuntimeModel findModel(Long appId, RuntimeEnvironment environment, String modelKey) {
        return runtimeModelMapper.selectOneByQuery(modelScope(appId, environment).eq("modelKey", modelKey));
    }

    private QueryWrapper modelScope(Long appId, RuntimeEnvironment environment) {
        return QueryWrapper.create()
                .eq("appId", appId)
                .eq("environment", environment.name());
    }

    private QueryWrapper recordScope(RuntimeModel model) {
        return QueryWrapper.create()
                .eq("appId", model.getAppId())
                .eq("environment", model.getEnvironment())
                .eq("modelKey", model.getModelKey())
                .eq("isDelete", 0);
    }

    private RuntimeApp requireRuntimeApp(String runtimeKey) {
        if (runtimeKey == null || !runtimeKey.matches("[a-f0-9]{32}")) throw params("运行面标识无效");
        RuntimeApp runtimeApp = runtimeAppMapper.selectOneByQuery(QueryWrapper.create().eq("runtimeKey", runtimeKey));
        if (runtimeApp == null || !Boolean.TRUE.equals(runtimeApp.getEnabled())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用运行面不存在或已停用");
        }
        return runtimeApp;
    }

    private RuntimeApp findRuntimeAppByAppId(Long appId) {
        return runtimeAppMapper.selectOneByQuery(QueryWrapper.create().eq("appId", appId));
    }

    private App requireOwner(Long appId, User user) {
        if (appId == null || appId <= 0) throw params("应用 ID 无效");
        if (user == null || user.getId() == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        App app = appMapper.selectOneById(appId);
        if (app == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!user.getId().equals(app.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只有应用所有者可以管理运行数据");
        }
        return app;
    }

    private boolean isOwner(Long appId, User user) {
        if (user == null || user.getId() == null) return false;
        App app = appMapper.selectOneById(appId);
        return app != null && user.getId().equals(app.getUserId());
    }

    private String validatedDataJson(RuntimeModelDefinition definition, Map<String, Object> data) {
        String json = writeJson(schemaValidator.validateRecord(definition, data));
        if (json.getBytes(StandardCharsets.UTF_8).length > RuntimeSchemaValidator.MAX_RECORD_BYTES) {
            throw params("单条数据不能超过 1 MB");
        }
        return json;
    }

    private RuntimeModelDefinition readDefinition(RuntimeModel model) {
        try {
            return objectMapper.readValue(model.getSchemaJson(), RuntimeModelDefinition.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据模型定义损坏");
        }
    }

    private Map<String, Object> readData(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据记录内容损坏");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw params("数据无法序列化为 JSON");
        }
    }

    private RuntimeConfigVO toConfigVO(RuntimeApp runtimeApp) {
        String base = "/api/runtime/v1/" + runtimeApp.getRuntimeKey();
        return RuntimeConfigVO.builder()
                .runtimeKey(runtimeApp.getRuntimeKey())
                .enabled(runtimeApp.getEnabled())
                .previewBaseUrl(base + "/preview")
                .productionBaseUrl(base + "/production")
                .build();
    }

    private RuntimeModelVO toModelVO(RuntimeModel model) {
        return RuntimeModelVO.builder()
                .id(String.valueOf(model.getId()))
                .environment(model.getEnvironment())
                .definition(readDefinition(model))
                .schemaVersion(model.getSchemaVersion())
                .status(model.getStatus())
                .updateTime(model.getUpdateTime())
                .build();
    }

    private RuntimeRecordVO toRecordVO(RuntimeRecord record) {
        return RuntimeRecordVO.builder()
                .id(String.valueOf(record.getId()))
                .modelKey(record.getModelKey())
                .data(readData(record.getDataJson()))
                .version(record.getRecordVersion())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }

    private String generateRuntimeKey() {
        String key;
        do {
            key = UUID.randomUUID().toString().replace("-", "");
        } while (runtimeAppMapper.selectCountByQuery(QueryWrapper.create().eq("runtimeKey", key)) > 0);
        return key;
    }

    private String normalizeModelKey(String modelKey) {
        String normalized = modelKey == null ? "" : modelKey.trim().toLowerCase();
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) throw params("模型标识格式错误");
        return normalized;
    }

    private BusinessException params(String message) {
        return new BusinessException(ErrorCode.PARAMS_ERROR, message);
    }

    private BusinessException operation(String message) {
        return new BusinessException(ErrorCode.OPERATION_ERROR, message);
    }
}
