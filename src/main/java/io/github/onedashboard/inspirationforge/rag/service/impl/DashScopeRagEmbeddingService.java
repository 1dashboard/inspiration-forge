package io.github.onedashboard.inspirationforge.rag.service.impl;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import io.github.onedashboard.inspirationforge.rag.config.RagEmbeddingProperties;
import io.github.onedashboard.inspirationforge.rag.service.RagEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DashScopeRagEmbeddingService implements RagEmbeddingService {
    private final RagEmbeddingProperties properties;

    public DashScopeRagEmbeddingService(RagEmbeddingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled()
                && "dashscope".equalsIgnoreCase(properties.getProvider())
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    @Override
    public Optional<List<Double>> embed(String text) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            TextEmbedding embedding = new TextEmbedding(properties.getApiKey());
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(properties.getModel())
                    .texts(List.of(text))
                    .dimension(properties.getDimension())
                    .build();
            TextEmbeddingResult result = embedding.call(param);
            if (result != null && result.getOutput() != null
                    && result.getOutput().getEmbeddings() != null
                    && !result.getOutput().getEmbeddings().isEmpty()) {
                return Optional.ofNullable(result.getOutput().getEmbeddings().getFirst().getEmbedding());
            }
        } catch (Exception e) {
            log.warn("RAG embedding 调用失败，将降级到关键词检索: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
