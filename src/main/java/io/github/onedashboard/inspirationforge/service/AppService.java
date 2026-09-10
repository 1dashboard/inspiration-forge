package io.github.onedashboard.inspirationforge.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import io.github.onedashboard.inspirationforge.model.dto.app.AppAddRequest;
import io.github.onedashboard.inspirationforge.model.dto.app.AppCodeFileUpdateRequest;
import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileMessage;
import io.github.onedashboard.inspirationforge.model.dto.app.AppQueryRequest;
import io.github.onedashboard.inspirationforge.model.dto.app.AppVersionFileRestoreRequest;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.model.entity.GenerationTask;
import io.github.onedashboard.inspirationforge.ai.model.GenerationPlan;
import io.github.onedashboard.inspirationforge.model.vo.AppVO;
import io.github.onedashboard.inspirationforge.model.vo.CodeFileDiffVO;
import io.github.onedashboard.inspirationforge.model.vo.BuildCheckVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author 1dashboard
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, GenerationPlan plan, User loginUser);

    /** Attach to an already running generation without starting a second task. */
    Flux<String> observeGeneration(Long appId, User loginUser);

    void cancelGeneration(Long appId, User loginUser);

    /** Continue the latest cancelled generation using the files already written to disk. */
    Flux<String> resumeGeneration(Long appId, User loginUser);

    GenerationTask getLatestGenerationTask(Long appId, User loginUser);

    GenerationPlan createGenerationPlan(Long appId, String prompt, User loginUser);

    BuildCheckVO checkBuild(Long appId, User loginUser);

    Flux<String> repairBuild(Long appId, int attempt, int maxAttempts, User loginUser);

    /**
     * 创建应用
     *
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 应用部署
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署地址
     */
    String deployApp(Long appId, User loginUser);

    String startDeployment(Long appId, User loginUser);

    void stopDeployment(Long appId, User loginUser);

    /** 将应用恢复到指定的历史版本。 */
    void rollbackVersion(Long appId, Long versionId, User loginUser);

    List<CodeFileDiffVO> diffVersion(Long appId, Long versionId, User loginUser);

    List<CodeFileDiffVO> diffVersions(Long appId, Long baseVersionId, Long targetVersionId, User loginUser);

    void restoreVersionFile(AppVersionFileRestoreRequest request, User loginUser);

    void saveCodeFile(AppCodeFileUpdateRequest request, User loginUser);

    List<CodeFileMessage> listCodeFiles(Long appId, User loginUser);

    int deleteApps(List<Long> appIds, User loginUser);

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

}
