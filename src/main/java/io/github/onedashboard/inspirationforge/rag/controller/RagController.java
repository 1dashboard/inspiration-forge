package io.github.onedashboard.inspirationforge.rag.controller;

import io.github.onedashboard.inspirationforge.common.BaseResponse;
import io.github.onedashboard.inspirationforge.common.ResultUtils;
import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.exception.ThrowUtils;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.rag.model.RagSearchResult;
import io.github.onedashboard.inspirationforge.rag.service.RagKnowledgeService;
import io.github.onedashboard.inspirationforge.service.AppService;
import io.github.onedashboard.inspirationforge.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagController {
    private final RagKnowledgeService ragKnowledgeService;
    private final AppService appService;
    private final UserService userService;

    public RagController(RagKnowledgeService ragKnowledgeService, AppService appService, UserService userService) {
        this.ragKnowledgeService = ragKnowledgeService;
        this.appService = appService;
        this.userService = userService;
    }

    @GetMapping("/search")
    public BaseResponse<List<RagSearchResult>> search(@RequestParam Long appId,
                                                       @RequestParam String query,
                                                       @RequestParam(defaultValue = "6") int limit,
                                                       HttpServletRequest request) {
        checkOwner(appId, request);
        return ResultUtils.success(ragKnowledgeService.search(appId, query, limit));
    }

    @PostMapping("/reindex")
    public BaseResponse<Boolean> reindex(@RequestParam Long appId, HttpServletRequest request) {
        User user = checkOwner(appId, request);
        App app = appService.getById(appId);
        Path projectPath = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getCodeGenType() + "_" + appId);
        ragKnowledgeService.indexProjectDirectory(appId, user.getId(), "CODE", projectPath);
        return ResultUtils.success(true);
    }

    @PostMapping("/toggle")
    public BaseResponse<Boolean> toggle(@RequestParam Long appId,
                                        @RequestParam boolean enabled,
                                        HttpServletRequest request) {
        User user = checkOwner(appId, request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf("GENERATING".equals(app.getGenerationStatus()), ErrorCode.OPERATION_ERROR,
                "生成任务进行中，暂时不能切换项目记忆");
        App update = new App();
        update.setId(appId);
        update.setRagEnabled(enabled);
        ThrowUtils.throwIf(!appService.updateById(update), ErrorCode.OPERATION_ERROR, "更新项目记忆设置失败");
        if (enabled) {
            Path projectPath = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getCodeGenType() + "_" + appId);
            ragKnowledgeService.indexProjectDirectoryIfEmpty(appId, user.getId(), "CODE", projectPath);
        }
        return ResultUtils.success(true);
    }

    private User checkOwner(Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "appId 无效");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(!app.getUserId().equals(user.getId()), ErrorCode.NO_AUTH_ERROR, "无权访问该应用知识库");
        return user;
    }
}
