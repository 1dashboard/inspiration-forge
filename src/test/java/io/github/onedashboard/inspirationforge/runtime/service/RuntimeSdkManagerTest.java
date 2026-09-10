package io.github.onedashboard.inspirationforge.runtime.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSdkManagerTest {

    @Test
    void generatedSdkSupportsStandardAndCompatibilityImports() {
        String source = new RuntimeSdkManager().source("0123456789abcdef0123456789abcdef");

        assertTrue(source.contains("export const yuRuntime"));
        assertTrue(source.contains("export default yuRuntime"));
        assertTrue(source.contains("yuRuntime.data ="));
        assertTrue(source.contains("pageNum: params.pageNum || params.page || 1"));
        assertTrue(source.contains("await yuRuntime.get(modelKey, recordId))?.version"));
        assertTrue(source.contains("window.__YU_RUNTIME_ENV__"));
        assertTrue(source.contains("host === 'localhost'"));
    }
}
