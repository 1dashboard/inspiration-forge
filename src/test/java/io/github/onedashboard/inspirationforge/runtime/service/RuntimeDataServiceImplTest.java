package io.github.onedashboard.inspirationforge.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeAppMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeModelMapper;
import io.github.onedashboard.inspirationforge.runtime.mapper.RuntimeRecordMapper;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeApp;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeModel;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeRecord;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeEnvironment;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeFieldType;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeFieldDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeRecordVO;
import io.github.onedashboard.inspirationforge.runtime.service.impl.RuntimeDataServiceImpl;
import io.github.onedashboard.inspirationforge.runtime.validation.RuntimeSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeDataServiceImplTest {

    private static final long APP_ID = 10L;
    private static final long OWNER_ID = 20L;
    private static final String RUNTIME_KEY = "0123456789abcdef0123456789abcdef";

    @Mock
    private RuntimeAppMapper runtimeAppMapper;
    @Mock
    private RuntimeModelMapper runtimeModelMapper;
    @Mock
    private RuntimeRecordMapper runtimeRecordMapper;
    @Mock
    private AppMapper appMapper;

    private RuntimeDataServiceImpl service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = new RuntimeDataServiceImpl();
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(service, "runtimeAppMapper", runtimeAppMapper);
        ReflectionTestUtils.setField(service, "runtimeModelMapper", runtimeModelMapper);
        ReflectionTestUtils.setField(service, "runtimeRecordMapper", runtimeRecordMapper);
        ReflectionTestUtils.setField(service, "appMapper", appMapper);
        ReflectionTestUtils.setField(service, "schemaValidator", new RuntimeSchemaValidator());
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
    }

    @Test
    void productionAccessIsDeniedToAnonymousUsersByDefault() throws Exception {
        stubRuntime(model(RuntimeEnvironment.PRODUCTION, Set.of()));

        assertThrows(BusinessException.class, () -> service.getAccessibleModel(
                RUNTIME_KEY, RuntimeEnvironment.PRODUCTION, "tasks", RuntimeOperation.READ, null));
    }

    @Test
    void productionReadRequiresAnExplicitPublicOperation() throws Exception {
        stubRuntime(model(RuntimeEnvironment.PRODUCTION, Set.of(RuntimeOperation.READ)));

        RuntimeModelDefinition definition = service.getAccessibleModel(
                RUNTIME_KEY, RuntimeEnvironment.PRODUCTION, "tasks", RuntimeOperation.READ, null);

        assertEquals("tasks", definition.getModelKey());
    }

    @Test
    void previewAccessRemainsOwnerOnlyEvenWhenOperationIsPublic() throws Exception {
        stubRuntime(model(RuntimeEnvironment.PREVIEW, Set.of(RuntimeOperation.READ)));

        assertThrows(BusinessException.class, () -> service.getAccessibleModel(
                RUNTIME_KEY, RuntimeEnvironment.PREVIEW, "tasks", RuntimeOperation.READ, null));
    }

    @Test
    void optimisticUpdateRejectsAConcurrentVersionChange() throws Exception {
        RuntimeModel model = model(RuntimeEnvironment.PREVIEW, Set.of());
        when(appMapper.selectOneById(APP_ID)).thenReturn(ownerApp());
        when(runtimeModelMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(model);

        RuntimeRecord record = new RuntimeRecord();
        record.setId(30L);
        record.setAppId(APP_ID);
        record.setEnvironment(RuntimeEnvironment.PREVIEW.name());
        record.setModelKey("tasks");
        record.setDataJson("{\"title\":\"before\"}");
        record.setRecordVersion(1);
        when(runtimeRecordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(record);
        when(runtimeRecordMapper.updateByQuery(any(RuntimeRecord.class), any(QueryWrapper.class))).thenReturn(0);

        User owner = new User();
        owner.setId(OWNER_ID);
        assertThrows(BusinessException.class, () -> service.updateRecord(APP_ID,
                RuntimeEnvironment.PREVIEW, "tasks", 30L, 1, Map.of("title", "after"), owner));
    }

    @Test
    void optimisticUpdateAdvancesTheVersion() throws Exception {
        RuntimeModel model = model(RuntimeEnvironment.PREVIEW, Set.of());
        when(appMapper.selectOneById(APP_ID)).thenReturn(ownerApp());
        when(runtimeModelMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(model);

        RuntimeRecord record = new RuntimeRecord();
        record.setId(30L);
        record.setAppId(APP_ID);
        record.setEnvironment(RuntimeEnvironment.PREVIEW.name());
        record.setModelKey("tasks");
        record.setDataJson("{\"title\":\"before\"}");
        record.setRecordVersion(1);
        when(runtimeRecordMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(record);
        when(runtimeRecordMapper.updateByQuery(any(RuntimeRecord.class), any(QueryWrapper.class))).thenReturn(1);

        User owner = new User();
        owner.setId(OWNER_ID);
        RuntimeRecordVO updated = service.updateRecord(APP_ID, RuntimeEnvironment.PREVIEW,
                "tasks", 30L, 1, Map.of("title", "after"), owner);

        assertEquals(2, updated.getVersion());
        assertEquals("after", updated.getData().get("title"));
    }

    @Test
    void runtimeConfigInsertIncludesRequiredTimestamps() {
        when(appMapper.selectOneById(APP_ID)).thenReturn(ownerApp());
        when(runtimeAppMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(runtimeAppMapper.insert(any(RuntimeApp.class))).thenReturn(1);

        service.ensureConfig(APP_ID, ownerUser());

        ArgumentCaptor<RuntimeApp> captor = ArgumentCaptor.forClass(RuntimeApp.class);
        verify(runtimeAppMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getCreateTime());
        assertNotNull(captor.getValue().getUpdateTime());
    }

    @Test
    void modelAndRecordInsertsIncludeRequiredMetadata() throws Exception {
        when(appMapper.selectOneById(APP_ID)).thenReturn(ownerApp());
        RuntimeApp runtimeApp = new RuntimeApp();
        runtimeApp.setAppId(APP_ID);
        runtimeApp.setRuntimeKey(RUNTIME_KEY);
        runtimeApp.setEnabled(true);
        when(runtimeAppMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(runtimeApp);
        when(runtimeModelMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(runtimeModelMapper.insert(any(RuntimeModel.class))).thenReturn(1);

        RuntimeModelDefinition definition = definition();
        io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest request =
                new io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest();
        request.setAppId(APP_ID);
        request.setDefinition(definition);
        service.upsertPreviewModel(request, ownerUser());

        ArgumentCaptor<RuntimeModel> modelCaptor = ArgumentCaptor.forClass(RuntimeModel.class);
        verify(runtimeModelMapper).insert(modelCaptor.capture());
        RuntimeModel insertedModel = modelCaptor.getValue();
        assertNotNull(insertedModel.getCreateTime());
        assertNotNull(insertedModel.getUpdateTime());
        assertEquals(0, insertedModel.getIsDelete());

        when(runtimeModelMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(model(RuntimeEnvironment.PREVIEW, Set.of()));
        when(runtimeRecordMapper.insert(any(RuntimeRecord.class))).thenReturn(1);
        service.createRecord(APP_ID, RuntimeEnvironment.PREVIEW, "tasks",
                Map.of("title", "test"), ownerUser());

        ArgumentCaptor<RuntimeRecord> recordCaptor = ArgumentCaptor.forClass(RuntimeRecord.class);
        verify(runtimeRecordMapper).insert(recordCaptor.capture());
        assertNotNull(recordCaptor.getValue().getCreateTime());
        assertNotNull(recordCaptor.getValue().getUpdateTime());
        assertEquals(0, recordCaptor.getValue().getIsDelete());
    }

    private void stubRuntime(RuntimeModel model) {
        RuntimeApp runtimeApp = new RuntimeApp();
        runtimeApp.setAppId(APP_ID);
        runtimeApp.setRuntimeKey(RUNTIME_KEY);
        runtimeApp.setEnabled(true);
        when(runtimeAppMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(runtimeApp);
        when(runtimeModelMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(model);
    }

    private RuntimeModel model(RuntimeEnvironment environment, Set<RuntimeOperation> publicOperations)
            throws Exception {
        RuntimeModelDefinition definition = definition();

        RuntimeModel model = new RuntimeModel();
        model.setId(40L);
        model.setAppId(APP_ID);
        model.setEnvironment(environment.name());
        model.setModelKey("tasks");
        model.setDisplayName("Tasks");
        model.setSchemaJson(objectMapper.writeValueAsString(definition));
        model.setSchemaVersion(1);
        model.setStatus(environment == RuntimeEnvironment.PRODUCTION ? "PUBLISHED" : "DRAFT");
        definition.setPublicOperations(new LinkedHashSet<>(publicOperations));
        model.setSchemaJson(objectMapper.writeValueAsString(definition));
        return model;
    }

    private RuntimeModelDefinition definition() {
        RuntimeFieldDefinition title = new RuntimeFieldDefinition();
        title.setKey("title");
        title.setName("Title");
        title.setType(RuntimeFieldType.STRING);
        title.setRequired(true);
        title.setMaxLength(255);

        RuntimeModelDefinition definition = new RuntimeModelDefinition();
        definition.setModelKey("tasks");
        definition.setDisplayName("Tasks");
        definition.setFields(List.of(title));
        definition.setPublicOperations(new LinkedHashSet<>());
        return definition;
    }

    private App ownerApp() {
        App app = new App();
        app.setId(APP_ID);
        app.setUserId(OWNER_ID);
        return app;
    }

    private User ownerUser() {
        User user = new User();
        user.setId(OWNER_ID);
        return user;
    }
}
