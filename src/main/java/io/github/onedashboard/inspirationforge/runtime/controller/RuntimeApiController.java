package io.github.onedashboard.inspirationforge.runtime.controller;

import io.github.onedashboard.inspirationforge.common.BaseResponse;
import io.github.onedashboard.inspirationforge.common.ResultUtils;
import io.github.onedashboard.inspirationforge.constant.UserConstant;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.exception.ThrowUtils;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.ratelimter.annotation.RateLimit;
import io.github.onedashboard.inspirationforge.ratelimter.enums.RateLimitType;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeDataMutationRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeAuthRequest;
import io.github.onedashboard.inspirationforge.runtime.model.dto.RuntimeDataQueryRequest;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeEnvironment;
import io.github.onedashboard.inspirationforge.runtime.model.enums.RuntimeOperation;
import io.github.onedashboard.inspirationforge.runtime.model.schema.RuntimeModelDefinition;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeModelVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimePageVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeRecordVO;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeAuthVO;
import io.github.onedashboard.inspirationforge.runtime.model.auth.RuntimeUserPrincipal;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeDataService;
import io.github.onedashboard.inspirationforge.runtime.service.RuntimeAuthService;
import io.github.onedashboard.inspirationforge.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/runtime/v1/{runtimeKey}/{environment}")
public class RuntimeApiController {

    @Resource
    private RuntimeDataService runtimeDataService;

    @Resource
    private UserService userService;

    @Resource
    private RuntimeAuthService runtimeAuthService;

    @PostMapping("/auth/register")
    public BaseResponse<RuntimeAuthVO> register(@PathVariable String runtimeKey, @RequestBody RuntimeAuthRequest request) {
        return ResultUtils.success(runtimeAuthService.register(runtimeKey, request));
    }

    @PostMapping("/auth/login")
    public BaseResponse<RuntimeAuthVO> login(@PathVariable String runtimeKey, @RequestBody RuntimeAuthRequest request) {
        return ResultUtils.success(runtimeAuthService.login(runtimeKey, request));
    }

    @PostMapping("/auth/logout")
    public BaseResponse<Boolean> logout(@PathVariable String runtimeKey, HttpServletRequest request) {
        runtimeAuthService.logout(runtimeKey, request);
        return ResultUtils.success(true);
    }

    @GetMapping("/auth/me")
    public BaseResponse<RuntimeUserPrincipal> me(@PathVariable String runtimeKey, HttpServletRequest request) {
        return ResultUtils.success(runtimeAuthService.optionalUser(runtimeKey, request));
    }

    @GetMapping("/models")
    @RateLimit(limitType = RateLimitType.IP, rate = 120, rateInterval = 60)
    public BaseResponse<List<RuntimeModelVO>> listModels(@PathVariable String runtimeKey,
                                                        @PathVariable String environment,
                                                        HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.listAccessibleModels(runtimeKey,
                RuntimeEnvironment.parse(environment), optionalPlatformUser(request), runtimeUser(runtimeKey, request)));
    }

    @GetMapping("/models/{modelKey}")
    @RateLimit(limitType = RateLimitType.IP, rate = 120, rateInterval = 60)
    public BaseResponse<RuntimeModelDefinition> getModel(@PathVariable String runtimeKey,
                                                        @PathVariable String environment,
                                                        @PathVariable String modelKey,
                                                        HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.getAccessibleModel(runtimeKey,
                RuntimeEnvironment.parse(environment), modelKey, RuntimeOperation.READ,
                optionalPlatformUser(request), runtimeUser(runtimeKey, request)));
    }

    @PostMapping("/records/{modelKey}/query")
    @RateLimit(limitType = RateLimitType.IP, rate = 240, rateInterval = 60)
    public BaseResponse<RuntimePageVO<RuntimeRecordVO>> queryRecords(@PathVariable String runtimeKey,
                                                                    @PathVariable String environment,
                                                                    @PathVariable String modelKey,
                                                                    @RequestBody(required = false)
                                                                    RuntimeDataQueryRequest queryRequest,
                                                                    HttpServletRequest request) {
        RuntimeDataQueryRequest query = queryRequest == null ? new RuntimeDataQueryRequest() : queryRequest;
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        return ResultUtils.success(runtimeDataService.queryAccessibleRecords(runtimeKey,
                RuntimeEnvironment.parse(environment), modelKey, pageNum, pageSize,
                optionalPlatformUser(request), runtimeUser(runtimeKey, request)));
    }

    @GetMapping("/records/{modelKey}/{recordId}")
    @RateLimit(limitType = RateLimitType.IP, rate = 240, rateInterval = 60)
    public BaseResponse<RuntimeRecordVO> getRecord(@PathVariable String runtimeKey,
                                                   @PathVariable String environment,
                                                   @PathVariable String modelKey,
                                                   @PathVariable Long recordId,
                                                   HttpServletRequest request) {
        return ResultUtils.success(runtimeDataService.getAccessibleRecord(runtimeKey,
                RuntimeEnvironment.parse(environment), modelKey, recordId, optionalPlatformUser(request), runtimeUser(runtimeKey, request)));
    }

    @PostMapping("/records/{modelKey}")
    @RateLimit(limitType = RateLimitType.IP, rate = 120, rateInterval = 60)
    public BaseResponse<RuntimeRecordVO> createRecord(@PathVariable String runtimeKey,
                                                      @PathVariable String environment,
                                                      @PathVariable String modelKey,
                                                      @RequestBody RuntimeDataMutationRequest mutationRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(mutationRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(runtimeDataService.createAccessibleRecord(runtimeKey,
                RuntimeEnvironment.parse(environment), modelKey, mutationRequest.getData(),
                optionalPlatformUser(request), runtimeUser(runtimeKey, request)));
    }

    @PatchMapping("/records/{modelKey}/{recordId}")
    @RateLimit(limitType = RateLimitType.IP, rate = 120, rateInterval = 60)
    public BaseResponse<RuntimeRecordVO> updateRecord(@PathVariable String runtimeKey,
                                                      @PathVariable String environment,
                                                      @PathVariable String modelKey,
                                                      @PathVariable Long recordId,
                                                      @RequestBody RuntimeDataMutationRequest mutationRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(mutationRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(runtimeDataService.updateAccessibleRecord(runtimeKey,
                RuntimeEnvironment.parse(environment), modelKey, recordId, mutationRequest.getExpectedVersion(),
                mutationRequest.getData(), optionalPlatformUser(request), runtimeUser(runtimeKey, request)));
    }

    @DeleteMapping("/records/{modelKey}/{recordId}")
    @RateLimit(limitType = RateLimitType.IP, rate = 120, rateInterval = 60)
    public BaseResponse<Boolean> deleteRecord(@PathVariable String runtimeKey,
                                              @PathVariable String environment,
                                              @PathVariable String modelKey,
                                              @PathVariable Long recordId,
                                              @RequestParam Integer expectedVersion,
                                              HttpServletRequest request) {
        runtimeDataService.deleteAccessibleRecord(runtimeKey, RuntimeEnvironment.parse(environment), modelKey,
                recordId, expectedVersion, optionalPlatformUser(request), runtimeUser(runtimeKey, request));
        return ResultUtils.success(true);
    }

    private User optionalPlatformUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(value instanceof User user) || user.getId() == null) return null;
        return userService.getById(user.getId());
    }

    private RuntimeUserPrincipal runtimeUser(String runtimeKey, HttpServletRequest request) {
        return runtimeAuthService.optionalUser(runtimeKey, request);
    }
}
