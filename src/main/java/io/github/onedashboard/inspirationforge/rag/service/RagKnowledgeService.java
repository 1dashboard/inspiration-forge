package io.github.onedashboard.inspirationforge.rag.service;

import io.github.onedashboard.inspirationforge.rag.model.RagSearchResult;
import io.github.onedashboard.inspirationforge.rag.model.entity.RagKnowledgeBase;

import java.nio.file.Path;
import java.util.List;

public interface RagKnowledgeService {
    RagKnowledgeBase ensureKnowledgeBase(Long appId, Long userId);

    void indexDocument(Long appId, Long userId, String sourceType, String sourceRef,
                       String title, String content, String metadataJson);

    void indexProjectDirectory(Long appId, Long userId, String sourceType, Path projectDirectory);

    void indexProjectDirectoryIfEmpty(Long appId, Long userId, String sourceType, Path projectDirectory);

    List<RagSearchResult> search(Long appId, String query, int limit);

    String augmentPrompt(Long appId, String userPrompt);

    void deleteByAppId(Long appId);
}
