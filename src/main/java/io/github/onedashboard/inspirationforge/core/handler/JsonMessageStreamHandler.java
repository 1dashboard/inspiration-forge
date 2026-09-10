package io.github.onedashboard.inspirationforge.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.github.onedashboard.inspirationforge.ai.model.message.*;
import io.github.onedashboard.inspirationforge.ai.tools.BaseTool;
import io.github.onedashboard.inspirationforge.ai.tools.ToolManager;
import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.model.enums.ChatHistoryMessageTypeEnum;
import io.github.onedashboard.inspirationforge.service.ChatHistoryService;
import io.github.onedashboard.inspirationforge.utils.AiErrorMessageUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

/** Converts tool-driven generation events into chat text and structured file events. */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds, appId))
                .filter(StrUtil::isNotEmpty)
                .doOnComplete(() -> chatHistoryService.addChatMessage(
                        appId, chatHistoryStringBuilder.toString(),
                        ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId()))
                .doOnError(error -> chatHistoryService.addChatMessage(
                        appId, "AI响应失败: " + AiErrorMessageUtils.friendly(error),
                        ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId()));
    }

    private String handleJsonMessageChunk(String chunk, StringBuilder history, Set<String> seenToolIds, long appId) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum type = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (type == null) return "";
        return switch (type) {
            case AI_RESPONSE -> {
                String data = JSONUtil.toBean(chunk, AiResponseMessage.class).getData();
                history.append(data);
                yield data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage request = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                if (request.getId() == null || !seenToolIds.add(request.getId())) yield "";
                BaseTool tool = toolManager.getTool(request.getName());
                yield tool == null ? "" : tool.generateToolRequestResponse();
            }
            case TOOL_EXECUTED -> handleToolExecuted(chunk, history, appId);
            case CODE_FILE_DELTA -> chunk;
        };
    }

    private String handleToolExecuted(String chunk, StringBuilder history, long appId) {
        ToolExecutedMessage executed = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        JSONObject args = JSONUtil.parseObj(executed.getArguments());
        String name = executed.getName();
        BaseTool tool = toolManager.getTool(name);
        String result = tool == null ? "工具执行完成" : tool.generateToolExecutedResult(args);
        if ("writeFile".equals(name) || "modifyFile".equals(name)) {
            String path = args.getStr("relativeFilePath");
            String content = readCurrentFile(appId, path);
            if (content == null) {
                content = "writeFile".equals(name) ? args.getStr("content") : args.getStr("newContent");
            }
            String summary = "[已更新文件] " + path;
            history.append("\n\n").append(summary).append("\n\n");
            return JSONUtil.toJsonStr(new CodeFileMessage(
                    path, content == null ? "" : content, detectLanguage(path),
                    "writeFile".equals(name) ? "write" : "modify", summary));
        }
        String output = "\n\n" + result + "\n\n";
        history.append(output);
        return output;
    }

    private String readCurrentFile(long appId, String relativePath) {
        if (relativePath == null || relativePath.contains("..")) return null;
        try {
            Path root = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId)
                    .toAbsolutePath().normalize();
            Path file = root.resolve(relativePath).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
            return Files.readString(file);
        } catch (Exception e) {
            return null;
        }
    }

    private String detectLanguage(String path) {
        if (path == null) return "text";
        String lower = path.toLowerCase();
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
}
