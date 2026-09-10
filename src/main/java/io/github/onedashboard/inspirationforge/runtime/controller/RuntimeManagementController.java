package io.github.onedashboard.inspirationforge.runtime.controller;

import io.github.onedashboard.inspirationforge.common.BaseResponse;
import io.github.onedashboard.inspirationforge.common.ResultUtils;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.exception.ThrowUtils;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelPublishRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeModelUpsertRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeRecordCreateRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeRecordDeleteRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeRecordQueryRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeRecordUpdateRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeUserRoleRequest;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeEnvironment;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeConfigVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeModelVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimePageVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeRecordVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeAuthVO;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeAuthService;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeDataService;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeSdkManager;
import io.github.onedashboard.inspirationforge.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/app/runtime")
public class RuntimeManagementController {

    @Resource
    private RuntimeDataService runtimeDataService;

    @Resource
    private UserService userService;

    @Resource
    private RuntimeSdkManager runtimeSdkManager;

    @Resource
    private RuntimeAuthService runtimeAuthService;

    @GetMapping("/user/list")
    public BaseResponse<List<RuntimeAuthVO>> listRuntimeUsers(@RequestParam Long appId, HttpServletRequest request) {
        return ResultUtils.success(runtimeAuthService.listUsers(appId, loginUser(request)));
    }

    @PostMapping("/user/role/assign")
    public BaseResponse<Boolean> assignRuntimeRole(@RequestBody RuntimeUserRoleRequest roleRequest,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(roleRequest == null, ErrorCode.PARAMS_ERROR);
        runtimeAuthService.assignRole(roleRequest.getAppId(), roleRequest.getUserId(), roleRequest.getRoleKey(), loginUser(request));
        return ResultUtils.success(true);
    }

    @PostMapping("/config/ensure")
    public BaseResponse<RuntimeConfigVO> ensureConfig(@RequestParam Long appId, HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.ensureConfig(appId, loginUser(request)));
    }

    @GetMapping("/config/get")
    public BaseResponse<RuntimeConfigVO> getConfig(@RequestParam Long appId, HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.getConfig(appId, loginUser(request)));
    }

    @GetMapping("/model/list")
    public BaseResponse<List<RuntimeModelVO>> listModels(@RequestParam Long appId,
                                                        @RequestParam(defaultValue = "PREVIEW") String environment,
                                                        HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.listModels(appId, RuntimeEnvironment.parse(environment),
                loginUser(request)));
    }

    @PostMapping("/model/upsert")
    public BaseResponse<RuntimeModelVO> upsertModel(@RequestBody RuntimeModelUpsertRequest modelRequest,
                                                   HttpServletRequest request) {
        User user = loginUser(request);
        RuntimeModelVO model = runtimeDataService.upsertPreviewModel(modelRequest, user);
        runtimeSdkManager.sync(modelRequest.getAppId(), runtimeDataService.getConfig(modelRequest.getAppId(), user));
        return ResultUtils.success(model);
    }

    @PostMapping("/model/publish")
    public BaseResponse<RuntimeModelVO> publishModel(@RequestBody RuntimeModelPublishRequest publishRequest,
                                                    HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.publishModel(publishRequest, loginUser(request)));
    }

    @PostMapping("/record/create")
    public BaseResponse<RuntimeRecordVO> createRecord(@RequestBody RuntimeRecordCreateRequest recordRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(recordRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(runtimeDataService.createRecord(recordRequest.getAppId(),
                RuntimeEnvironment.parse(recordRequest.getEnvironment()), recordRequest.getModelKey(),
                recordRequest.getData(), loginUser(request)));
    }

    @PostMapping("/record/update")
    public BaseResponse<RuntimeRecordVO> updateRecord(@RequestBody RuntimeRecordUpdateRequest recordRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(recordRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(runtimeDataService.updateRecord(recordRequest.getAppId(),
                RuntimeEnvironment.parse(recordRequest.getEnvironment()), recordRequest.getModelKey(),
                recordRequest.getRecordId(), recordRequest.getExpectedVersion(), recordRequest.getData(),
                loginUser(request)));
    }

    @PostMapping("/record/delete")
    public BaseResponse<Boolean> deleteRecord(@RequestBody RuntimeRecordDeleteRequest recordRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(recordRequest == null, ErrorCode.PARAMS_ERROR);
        runtimeDataService.deleteRecord(recordRequest.getAppId(),
                RuntimeEnvironment.parse(recordRequest.getEnvironment()), recordRequest.getModelKey(),
                recordRequest.getRecordId(), recordRequest.getExpectedVersion(), loginUser(request));
        return ResultUtils.success(true);
    }

    @GetMapping("/record/get")
    public BaseResponse<RuntimeRecordVO> getRecord(@RequestParam Long appId,
                                                   @RequestParam String environment,
                                                   @RequestParam String modelKey,
                                                   @RequestParam Long recordId,
                                                   HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.getRecord(appId, RuntimeEnvironment.parse(environment),
                modelKey, recordId, loginUser(request)));
    }

    @PostMapping("/record/query")
    public BaseResponse<RuntimePageVO<RuntimeRecordVO>> queryRecords(
            @RequestBody RuntimeRecordQueryRequest queryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = queryRequest.getPageNum() == null ? 1 : queryRequest.getPageNum();
        int pageSize = queryRequest.getPageSize() == null ? 20 : queryRequest.getPageSize();
        return ResultUtils.success(runtimeDataService.queryRecords(queryRequest.getAppId(),
                RuntimeEnvironment.parse(queryRequest.getEnvironment()), queryRequest.getModelKey(),
                pageNum, pageSize, loginUser(request)));
    }

    private User loginUser(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }
}
