package io.github.onedashboard.inspirationforge.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPathGuardTest {

    private final ProjectPathGuard guard = new ProjectPathGuard();

    @Test
    void recognizesThePlatformManagedRuntimeSdkAcrossPathStyles() {
        assertTrue(guard.isPlatformManagedFile("src/lib/yuRuntime.js"));
        assertTrue(guard.isPlatformManagedFile("SRC\\LIB\\YURUNTIME.JS"));
        assertFalse(guard.isPlatformManagedFile("src/lib/api.js"));
    }
}
