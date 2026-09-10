package io.github.onedashboard.inspirationforge.rag.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RagSearchResult {
    Long chunkId;
    String title;
    String sourceType;
    String sourceRef;
    String content;
    double score;
}
