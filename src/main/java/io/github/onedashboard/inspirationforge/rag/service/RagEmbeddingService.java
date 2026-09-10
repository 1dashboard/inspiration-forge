package io.github.onedashboard.inspirationforge.rag.service;

import java.util.List;
import java.util.Optional;

public interface RagEmbeddingService {
    boolean isAvailable();

    Optional<List<Double>> embed(String text);
}
