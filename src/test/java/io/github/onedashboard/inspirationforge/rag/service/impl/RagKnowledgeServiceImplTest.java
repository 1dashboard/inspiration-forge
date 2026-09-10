package io.github.onedashboard.inspirationforge.rag.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagKnowledgeServiceImplTest {

    @Test
    void shouldSplitLongContentWithOverlap() {
        String content = "a".repeat(1700) + "\n" + "b".repeat(1700);

        List<String> chunks = RagKnowledgeServiceImpl.splitIntoChunks(content);

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 1800));
        assertEquals('a', chunks.getFirst().charAt(0));
        assertEquals('b', chunks.getLast().charAt(chunks.getLast().length() - 1));
    }

    @Test
    void shouldCreateChineseNgramsAndEnglishTerms() {
        List<String> terms = RagKnowledgeServiceImpl.tokenize("把登录按钮改成蓝色 Vue component");

        assertTrue(terms.contains("登录"));
        assertTrue(terms.contains("按钮"));
        assertTrue(terms.contains("蓝色"));
        assertTrue(terms.contains("vue"));
        assertTrue(terms.contains("component"));
    }

    @Test
    void shouldIgnoreDependenciesAndBuildOutput() {
        Path root = Path.of("project").toAbsolutePath();

        assertTrue(RagKnowledgeServiceImpl.isIgnoredPath(root, root.resolve("node_modules/pkg/index.js")));
        assertTrue(RagKnowledgeServiceImpl.isIgnoredPath(root, root.resolve("dist/assets/index.js")));
        assertTrue(!RagKnowledgeServiceImpl.isIgnoredPath(root, root.resolve("src/views/Home.vue")));
    }
}
