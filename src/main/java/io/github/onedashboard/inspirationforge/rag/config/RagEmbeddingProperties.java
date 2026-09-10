package io.github.onedashboard.inspirationforge.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.embedding")
public class RagEmbeddingProperties {
    private boolean enabled = false;
    private String provider = "dashscope";
    private String apiKey;
    private String model = "text-embedding-v3";
    private Integer dimension = 1024;
}
