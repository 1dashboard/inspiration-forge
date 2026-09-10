package io.github.onedashboard.inspirationforge.runtime.service;

import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelPublishRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeEnvironment;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeConfigVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeModelVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimePageVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeRecordVO;
import io.github.onedashboard.inspirationforge.runtime.model.auth.RuntimeUserPrincipal;

import java.util.List;
import java.util.Map;

public interface RuntimeDataService {
    void deleteByAppId(Long appId);

    RuntimeConfigVO ensureConfig(Long appId, User platformUser);

    RuntimeConfigVO getConfig(Long appId, User platformUser);

    List<RuntimeModelVO> listModels(Long appId, RuntimeEnvironment environment, User platformUser);

    RuntimeModelVO upsertPreviewModel(RuntimeModelUpsertRequest request, User platformUser);

    RuntimeModelVO publishModel(RuntimeModelPublishRequest request, User platformUser);

    RuntimeRecordVO createRecord(Long appId, RuntimeEnvironment environment, String modelKey,
                                 Map<String, Object> data, User platformUser);

    RuntimeRecordVO updateRecord(Long appId, RuntimeEnvironment environment, String modelKey, Long recordId,
                                 Integer expectedVersion, Map<String, Object> patch, User platformUser);

    void deleteRecord(Long appId, RuntimeEnvironment environment, String modelKey, Long recordId,
                      Integer expectedVersion, User platformUser);

    RuntimeRecordVO getRecord(Long appId, RuntimeEnvironment environment, String modelKey, Long recordId,
                              User platformUser);

    RuntimePageVO<RuntimeRecordVO> queryRecords(Long appId, RuntimeEnvironment environment, String modelKey,
                                                int pageNum, int pageSize, User platformUser);

    List<RuntimeModelVO> listAccessibleModels(String runtimeKey, RuntimeEnvironment environment,
                                              User platformUser, RuntimeUserPrincipal runtimeUser);

    default List<RuntimeModelVO> listAccessibleModels(String runtimeKey, RuntimeEnvironment environment, User platformUser) {
        return listAccessibleModels(runtimeKey, environment, platformUser, null);
    }

    RuntimeModelDefinition getAccessibleModel(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                              RuntimeOperation operation, User platformUser, RuntimeUserPrincipal runtimeUser);

    default RuntimeModelDefinition getAccessibleModel(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                                       RuntimeOperation operation, User platformUser) {
        return getAccessibleModel(runtimeKey, environment, modelKey, operation, platformUser, null);
    }

    RuntimeRecordVO createAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                           Map<String, Object> data, User platformUser, RuntimeUserPrincipal runtimeUser);

    RuntimeRecordVO updateAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                           Long recordId, Integer expectedVersion, Map<String, Object> patch,
                                           User platformUser, RuntimeUserPrincipal runtimeUser);

    void deleteAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey, Long recordId,
                                Integer expectedVersion, User platformUser, RuntimeUserPrincipal runtimeUser);

    RuntimeRecordVO getAccessibleRecord(String runtimeKey, RuntimeEnvironment environment, String modelKey,
                                        Long recordId, User platformUser, RuntimeUserPrincipal runtimeUser);

    RuntimePageVO<RuntimeRecordVO> queryAccessibleRecords(String runtimeKey, RuntimeEnvironment environment,
                                                          String modelKey, int pageNum, int pageSize,
                                                          User platformUser, RuntimeUserPrincipal runtimeUser);
}
