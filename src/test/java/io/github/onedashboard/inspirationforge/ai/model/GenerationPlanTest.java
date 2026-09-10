package io.github.onedashboard.inspirationforge.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationPlanTest {

    @Test
    void databaseCapabilityIsOptIn() {
        GenerationPlan plan = new GenerationPlan();

        assertFalse(plan.getNeedsDatabase());
        assertNotNull(plan.getDataModels());
        assertTrue(plan.getDataModels().isEmpty());
    }
}
