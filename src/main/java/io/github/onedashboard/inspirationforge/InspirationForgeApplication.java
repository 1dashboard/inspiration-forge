package io.github.onedashboard.inspirationforge;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan({"io.github.onedashboard.inspirationforge.mapper", "io.github.onedashboard.inspirationforge.runtime.mapper", "io.github.onedashboard.inspirationforge.rag.mapper"})
public class InspirationForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(InspirationForgeApplication.class, args);
    }

}
