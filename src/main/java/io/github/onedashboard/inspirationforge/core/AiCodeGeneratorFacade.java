package io.github.onedashboard.inspirationforge.core;

import cn.hutool.json.JSONUtil;
import io.github.onedashboard.inspirationforge.ai.AiCodeGeneratorService;
import io.github.onedashboard.inspirationforge.ai.AiCodeGeneratorServiceFactory;
import io.github.onedashboard.inspirationforge.ai.GenerationMode;
import io.github.onedashboard.inspirationforge.ai.model.HtmlCodeResult;
import io.github.onedashboard.inspirationforge.ai.model.MultiFileCodeResult;
import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileMessage;
import io.github.onedashboard.inspirationforge.ai.model.message.AiResponseMessage;
import io.github.onedashboard.inspirationforge.ai.model.message.ToolExecutedMessage;
import io.github.onedashboard.inspirationforge.ai.model.message.ToolRequestMessage;
import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.core.builder.VueProjectBuilder;
import io.github.onedashboard.inspirationforge.core.parser.CodeParserExecutor;
import io.github.onedashboard.inspirationforge.core.saver.CodeFileSaverExecutor;
import io.github.onedashboard.inspirationforge.core.streaming.MarkdownCodeBlockStreamParser;
import io.github.onedashboard.inspirationforge.core.streaming.ToolCallCodeStreamParser;
import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;

/**
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, GenerationMode.CREATE);
    }

    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                                   Long appId, GenerationMode mode) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(appId, codeGenTypeEnum, mode);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = mode == GenerationMode.MODIFY
                        ? aiCodeGeneratorService.modifyHtmlCodeStream(userMessage)
                        : aiCodeGeneratorService.createHtmlCodeStream(userMessage);
                yield processCodeStreamStructured(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = mode == GenerationMode.MODIFY
                        ? aiCodeGeneratorService.modifyMultiFileCodeStream(userMessage)
                        : aiCodeGeneratorService.createMultiFileCodeStream(userMessage);
                yield processCodeStreamStructured(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = mode == GenerationMode.MODIFY
                        ? aiCodeGeneratorService.modifyVueProjectCodeStream(appId, userMessage)
                        : aiCodeGeneratorService.createVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.create(sink -> {
            Map<String, ToolCallCodeStreamParser> toolStreamParsers = new ConcurrentHashMap<>();
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                        String parserKey = toolExecutionRequest.id() == null
                                ? index + ":" + toolExecutionRequest.name()
                                : toolExecutionRequest.id();
                        if ("writeFile".equals(toolExecutionRequest.name())
                                || "modifyFile".equals(toolExecutionRequest.name())) {
                            ToolCallCodeStreamParser parser = toolStreamParsers.computeIfAbsent(parserKey,
                                    key -> new ToolCallCodeStreamParser(key, toolExecutionRequest.name()));
                            var delta = parser.accept(toolExecutionRequest.arguments());
                            if (delta != null) sink.next(JSONUtil.toJsonStr(delta));
                        }
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        if (toolExecution.request().id() != null) {
                            toolStreamParsers.remove(toolExecution.request().id());
                        }
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // 字符串拼接器，用于当流式返回所有的代码之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后，保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    /** Buffer text-only generators so raw source is never rendered in the chat bubble. */
    private Flux<String> processCodeStreamStructured(Flux<String> codeStream,
                                                       CodeGenTypeEnum codeGenType, Long appId) {
        return Flux.create(sink -> {
            StringBuilder codeBuilder = new StringBuilder();
            MarkdownCodeBlockStreamParser streamParser = new MarkdownCodeBlockStreamParser(codeGenType);
            var subscription = codeStream.subscribe(chunk -> {
                codeBuilder.append(chunk);
                streamParser.accept(chunk).forEach(delta -> sink.next(JSONUtil.toJsonStr(delta)));
            }, sink::error, () -> {
                try {
                    streamParser.finish().forEach(delta -> sink.next(JSONUtil.toJsonStr(delta)));
                    Object parsedResult = CodeParserExecutor.executeParser(codeBuilder.toString(), codeGenType);
                    File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                    log.info("代码保存成功，目录为：{}", saveDir.getAbsolutePath());
                    if (codeGenType == CodeGenTypeEnum.HTML) {
                        HtmlCodeResult result = (HtmlCodeResult) parsedResult;
                        emitFile(sink::next, "index.html", result.getHtmlCode(), "html");
                    } else {
                        MultiFileCodeResult result = (MultiFileCodeResult) parsedResult;
                        emitFile(sink::next, "index.html", result.getHtmlCode(), "html");
                        emitFile(sink::next, "style.css", result.getCssCode(), "css");
                        emitFile(sink::next, "script.js", result.getJsCode(), "javascript");
                    }
                    sink.complete();
                } catch (Exception e) {
                    log.error("代码解析或保存失败", e);
                    sink.error(e);
                }
            });
            sink.onCancel(subscription::dispose);
        });
    }

    private void emitFile(Consumer<String> emitter, String path, String content, String language) {
        if (content == null || content.isBlank()) return;
        emitter.accept(JSONUtil.toJsonStr(new CodeFileMessage(
                path, content, language, "write", "[已生成文件] " + path)));
    }
}
