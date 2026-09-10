package io.github.onedashboard.inspirationforge.ai;

import io.github.onedashboard.inspirationforge.ai.model.HtmlCodeResult;
import io.github.onedashboard.inspirationforge.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-html-create-system-prompt.txt")
    Flux<String> createHtmlCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-html-modify-system-prompt.txt")
    Flux<String> modifyHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-multi-file-create-system-prompt.txt")
    Flux<String> createMultiFileCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-multi-file-modify-system-prompt.txt")
    Flux<String> modifyMultiFileCodeStream(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

    /** Initial project creation uses a constrained prompt and creation-only tools. */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-create-system-prompt.txt")
    TokenStream createVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

    /** Subsequent edits use a separate maintenance prompt and inspection tools. */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-modify-system-prompt.txt")
    TokenStream modifyVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
}
