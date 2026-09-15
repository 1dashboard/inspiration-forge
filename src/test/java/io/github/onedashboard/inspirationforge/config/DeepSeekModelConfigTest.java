package io.github.onedashboard.inspirationforge.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepSeekModelConfigTest {

    @Test
    void regularStreamingModelDisablesThinking() {
        StreamingChatModelConfig config = new StreamingChatModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("http://localhost");
        config.setModelName("deepseek-flash");
        config.setReasoningEffort("none");

        OpenAiStreamingChatModel model =
                (OpenAiStreamingChatModel) config.streamingChatModelPrototype();

        assertEquals("deepseek-flash", model.defaultRequestParameters().modelName());
        assertEquals("none", model.defaultRequestParameters().reasoningEffort());
    }

    @Test
    void vueProjectModelUsesHighReasoningEffort() {
        ReasoningStreamingChatModelConfig config = new ReasoningStreamingChatModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("http://localhost");
        config.setModelName("deepseek-flash");
        config.setReasoningEffort("high");

        OpenAiStreamingChatModel model =
                (OpenAiStreamingChatModel) config.reasoningStreamingChatModelPrototype();

        assertEquals("deepseek-flash", model.defaultRequestParameters().modelName());
        assertEquals("high", model.defaultRequestParameters().reasoningEffort());
    }
}
