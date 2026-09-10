package io.github.onedashboard.inspirationforge.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.github.onedashboard.inspirationforge.annotation.AuthCheck;
import io.github.onedashboard.inspirationforge.common.BaseResponse;
import io.github.onedashboard.inspirationforge.common.DeleteRequest;
import io.github.onedashboard.inspirationforge.common.ResultUtils;
import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.constant.UserConstant;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.exception.ThrowUtils;
import io.github.onedashboard.inspirationforge.model.dto.app.*;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.model.vo.AppVO;
import io.github.onedashboard.inspirationforge.ratelimter.annotation.RateLimit;
import io.github.onedashboard.inspirationforge.ratelimter.enums.RateLimitType;
import io.github.onedashboard.inspirationforge.service.ProjectDownloadService;
import io.github.onedashboard.inspirationforge.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.GenerationTask;
import io.github.onedashboard.inspirationforge.ai.model.GenerationPlan;
import io.github.onedashboard.inspirationforge.service.AppService;
import io.github.onedashboard.inspirationforge.service.AppVersionService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author 1dashboard
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    @Resource
    private ProjectDownloadService projectDownloadService;

    @Resource
    private AppVersionService appVersionService;

    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       @RequestParam(required = false) String plan,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        GenerationPlan generationPlan = null;
        if (StrUtil.isNotBlank(plan)) {
            try {
                generationPlan = JSONUtil.toBean(plan, GenerationPlan.class);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成计划格式错误");
            }
        }
        // 调用服务生成代码（SSE 流式返回）
        return toSseStream(appService.chatToGenCode(appId, message, generationPlan, loginUser));
    }

    /**
     * Starts generation with a JSON body. The browser then attaches to /chat/task/stream for SSE.
     * This avoids losing long prompts and generation plans to HTTP request-line/header limits.
     */
    @PostMapping("/chat/start")
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60,
            message = "AI 对话请求过于频繁，请稍后再试")
    public BaseResponse<Boolean> startGeneration(@RequestBody CodeGenerationRequest generationRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(generationRequest == null, ErrorCode.PARAMS_ERROR, "生成参数不能为空");
        ThrowUtils.throwIf(generationRequest.getAppId() == null || generationRequest.getAppId() <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(generationRequest.getMessage()),
                ErrorCode.PARAMS_ERROR, "提示词不能为空");
        appService.chatToGenCode(generationRequest.getAppId(), generationRequest.getMessage(),
                generationRequest.getPlan(), userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @GetMapping(value = "/chat/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> resumeGeneration(@RequestParam Long appId,
                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return toSseStream(appService.resumeGeneration(appId, loginUser));
    }

    @GetMapping(value = "/chat/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> observeGeneration(@RequestParam Long appId,
                                                            HttpServletRequest request) {
        return toSseStream(appService.observeGeneration(appId, userService.getLoginUser(request)));
    }

    private Flux<ServerSentEvent<String>> toSseStream(Flux<String> contentFlux) {
        return contentFlux
                .map(chunk -> {
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(doneEvent()))
                .onErrorResume(error -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("business-error")
                                .data(JSONUtil.toJsonStr(Map.of(
                                        "error", true,
                                        "code", ErrorCode.SYSTEM_ERROR.getCode(),
                                        "message", friendlyAiError(error))))
                                .build(),
                        doneEvent()));
    }

    private ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder().event("done").data("").build();
    }

    /** Never expose provider response bodies or API identifiers to the browser. */
    private String friendlyAiError(Throwable error) {
        String detail = error == null ? "" : error.toString();
        Throwable cause = error;
        while (cause != null) {
            detail += " " + String.valueOf(cause.getMessage());
            cause = cause.getCause();
        }
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
        return "AI 生成服务暂时不可用，请稍后重试。";
    }

    @PostMapping("/chat/cancel")
    public BaseResponse<Boolean> cancelGeneration(@RequestParam Long appId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        appService.cancelGeneration(appId, loginUser);
        return ResultUtils.success(true);
    }

    @GetMapping("/chat/task/latest")
    public BaseResponse<GenerationTask> latestGenerationTask(@RequestParam Long appId,
                                                              HttpServletRequest request) {
        return ResultUtils.success(appService.getLatestGenerationTask(appId, userService.getLoginUser(request)));
    }

    @PostMapping("/chat/plan")
    public BaseResponse<GenerationPlan> generationPlan(@RequestBody GenerationPlanRequest planRequest,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(planRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appService.createGenerationPlan(
                planRequest.getAppId(), planRequest.getPrompt(), userService.getLoginUser(request)));
    }

    @PostMapping("/build/check")
    public BaseResponse<io.github.onedashboard.inspirationforge.model.vo.BuildCheckVO> checkBuild(
            @RequestParam Long appId, HttpServletRequest request) {
        return ResultUtils.success(appService.checkBuild(appId, userService.getLoginUser(request)));
    }

    @GetMapping(value = "/chat/repair/build", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60,
            message = "AI 修复请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> repairBuild(@RequestParam Long appId,
                                                     @RequestParam(defaultValue = "1") int attempt,
                                                     @RequestParam(defaultValue = "3") int maxAttempts,
                                                     HttpServletRequest request) {
        return toSseStream(appService.repairBuild(appId, attempt, maxAttempts,
                userService.getLoginUser(request)));
    }

    @GetMapping("/version/list")
    public BaseResponse<List<io.github.onedashboard.inspirationforge.model.entity.AppVersion>> listVersions(
            @RequestParam Long appId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()), ErrorCode.NO_AUTH_ERROR);
        return ResultUtils.success(appVersionService.listByAppId(appId));
    }

    @PostMapping("/version/rollback")
    public BaseResponse<Boolean> rollbackVersion(@RequestParam Long appId,
                                                  @RequestParam Long versionId,
                                                  HttpServletRequest request) {
        appService.rollbackVersion(appId, versionId, userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @GetMapping("/version/diff")
    public BaseResponse<List<io.github.onedashboard.inspirationforge.model.vo.CodeFileDiffVO>> diffVersion(
            @RequestParam Long appId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) Long baseVersionId,
            @RequestParam(required = false) Long targetVersionId,
            HttpServletRequest request) {
        Long baseId = baseVersionId != null ? baseVersionId : versionId;
        ThrowUtils.throwIf(baseId == null, ErrorCode.PARAMS_ERROR, "请选择基准版本");
        return ResultUtils.success(appService.diffVersions(appId, baseId, targetVersionId,
                userService.getLoginUser(request)));
    }

    @PostMapping("/version/file/restore")
    public BaseResponse<Boolean> restoreVersionFile(@RequestBody AppVersionFileRestoreRequest restoreRequest,
                                                     HttpServletRequest request) {
        appService.restoreVersionFile(restoreRequest, userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @PostMapping("/code/file")
    public BaseResponse<Boolean> saveCodeFile(@RequestBody AppCodeFileUpdateRequest request,
                                               HttpServletRequest httpRequest) {
        appService.saveCodeFile(request, userService.getLoginUser(httpRequest));
        return ResultUtils.success(true);
    }

    @GetMapping("/code/files")
    public BaseResponse<List<io.github.onedashboard.inspirationforge.ai.model.message.CodeFileMessage>> listCodeFiles(
            @RequestParam Long appId, HttpServletRequest httpRequest) {
        return ResultUtils.success(appService.listCodeFiles(appId, userService.getLoginUser(httpRequest)));
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        // 检查部署请求是否为空
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取应用 ID
        Long appId = appDeployRequest.getAppId();
        // 检查应用 ID 是否为空
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        // 返回部署 URL
        return ResultUtils.success(deployUrl);
    }

    @PostMapping("/deploy/start")
    public BaseResponse<String> startDeployment(@RequestParam Long appId, HttpServletRequest request) {
        return ResultUtils.success(appService.startDeployment(appId, userService.getLoginUser(request)));
    }

    @PostMapping("/deploy/stop")
    public BaseResponse<Boolean> stopDeployment(@RequestParam Long appId, HttpServletRequest request) {
        appService.stopDeployment(appId, userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }

    /**
     * 创建应用
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    @PostMapping("/batch/delete")
    public BaseResponse<Integer> deleteApps(@RequestBody AppBatchDeleteRequest batchDeleteRequest,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(batchDeleteRequest == null || batchDeleteRequest.getIds() == null,
                ErrorCode.PARAMS_ERROR, "请选择要删除的应用");
        return ResultUtils.success(appService.deleteApps(
                batchDeleteRequest.getIds(), userService.getLoginUser(request)));
    }

    /**
     * 根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(io.github.onedashboard.inspirationforge.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }
}
