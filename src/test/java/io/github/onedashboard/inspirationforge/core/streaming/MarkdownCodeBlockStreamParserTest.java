package io.github.onedashboard.inspirationforge.core.streaming;

import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileDeltaMessage;
import io.github.onedashboard.inspirationforge.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownCodeBlockStreamParserTest {

    @Test
    void streamsMultipleMarkdownCodeBlocksIntoFiles() {
        MarkdownCodeBlockStreamParser parser = new MarkdownCodeBlockStreamParser(CodeGenTypeEnum.MULTI_FILE);
        List<CodeFileDeltaMessage> events = new ArrayList<>();
        events.addAll(parser.accept("plan\n```ht"));
        events.addAll(parser.accept("ml\n<h1>Hello</h1>\n```\n```css\nbody{"));
        events.addAll(parser.accept("color:red}\n```\n```js\nconsole.log('ok')"));
        events.addAll(parser.accept("\n```"));
        events.addAll(parser.finish());

        assertEquals("<h1>Hello</h1>\n", contentFor(events, "index.html"));
        assertEquals("body{color:red}\n", contentFor(events, "style.css"));
        assertEquals("console.log('ok')\n", contentFor(events, "script.js"));
    }

    private String contentFor(List<CodeFileDeltaMessage> events, String path) {
        return events.stream()
                .filter(event -> path.equals(event.getPath()))
                .map(CodeFileDeltaMessage::getDelta)
                .collect(Collectors.joining());
    }
}
