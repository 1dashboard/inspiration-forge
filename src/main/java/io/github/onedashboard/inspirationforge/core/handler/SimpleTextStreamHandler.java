package io.github.onedashboard.inspirationforge.core.handler;

import cn.hutool.json.JSONUtil;
import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileMessage;
import io.github.onedashboard.inspirationforge.model.entity.User;
import io.github.onedashboard.inspirationforge.model.enums.ChatHistoryMessageTypeEnum;
import io.github.onedashboard.inspirationforge.service.ChatHistoryService;
import io.github.onedashboard.inspirationforge.utils.AiErrorMessageUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {


    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    // 收集AI响应内容
                    try {
                        CodeFileMessage file = JSONUtil.toBean(chunk, CodeFileMessage.class);
                        if ("code_file".equals(file.getType())) {
                            aiResponseBuilder.append("\n\n").append(file.getSummary()).append("\n\n");
                            return chunk;
                        }
                        if ("code_file_delta".equals(file.getType())) {
                            return chunk;
                        }
                    } catch (Exception ignored) {
                        // Normal text chunks are not structured file events.
                    }
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + AiErrorMessageUtils.friendly(error);
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }
}
