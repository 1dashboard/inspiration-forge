package io.github.onedashboard.inspirationforge.core.streaming;

import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileDeltaMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallCodeStreamParserTest {

    @Test
    void decodesEscapedContentAcrossArgumentChunks() {
        ToolCallCodeStreamParser parser = new ToolCallCodeStreamParser("tool-1", "writeFile");
        List<CodeFileDeltaMessage> events = new ArrayList<>();
        add(events, parser.accept("{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template>\\n"));
        add(events, parser.accept("  <h1>\\u4f60\\u597d<\\/h1>\\n"));
        add(events, parser.accept("</template>\"}"));

        assertFalse(events.isEmpty());
        assertEquals("src/App.vue", events.getFirst().getPath());
        assertTrue(events.getFirst().isReset());
        assertEquals("<template>\n  <h1>你好</h1>\n</template>",
                events.stream().map(CodeFileDeltaMessage::getDelta).reduce("", String::concat));
    }

    @Test
    void streamsNewContentForModifyTool() {
        ToolCallCodeStreamParser parser = new ToolCallCodeStreamParser("tool-2", "modifyFile");
        parser.accept("{\"relativeFilePath\":\"style.css\",\"oldContent\":\"red\",");
        CodeFileDeltaMessage event = parser.accept("\"newContent\":\"blue\"}");

        assertEquals("modify", event.getOperation());
        assertEquals("blue", event.getDelta());
        assertEquals("css", event.getLanguage());
    }

    private void add(List<CodeFileDeltaMessage> events, CodeFileDeltaMessage event) {
        if (event != null) events.add(event);
    }
}
