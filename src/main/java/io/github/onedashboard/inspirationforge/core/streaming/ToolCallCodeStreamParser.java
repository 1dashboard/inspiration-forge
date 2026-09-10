package io.github.onedashboard.inspirationforge.core.streaming;

import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileDeltaMessage;

/**
 * Incrementally extracts file paths and code from streamed writeFile/modifyFile JSON arguments.
 * The parser keeps JSON escape state across chunks, so deltas are valid decoded source text.
 */
public class ToolCallCodeStreamParser {

    private final String streamId;
    private final String operation;
    private final StringBuilder token = new StringBuilder();
    private final StringBuilder pendingDelta = new StringBuilder();

    private boolean inString;
    private boolean readingKey;
    private boolean expectingValue;
    private boolean escaped;
    private int unicodeDigitsRemaining;
    private int unicodeValue;
    private String currentKey;
    private String activeValueKey;
    private String path;
    private boolean started;
    private boolean pathJustCompleted;

    public ToolCallCodeStreamParser(String streamId, String toolName) {
        this.streamId = streamId;
        this.operation = "modifyFile".equals(toolName) ? "modify" : "write";
    }

    public CodeFileDeltaMessage accept(String argumentsChunk) {
        if (argumentsChunk == null || argumentsChunk.isEmpty()) return null;
        pathJustCompleted = false;
        for (int index = 0; index < argumentsChunk.length(); index++) {
            consume(argumentsChunk.charAt(index));
        }
        if (path == null || (pendingDelta.isEmpty() && (!pathJustCompleted || started))) return null;

        String delta = pendingDelta.toString();
        pendingDelta.setLength(0);
        boolean reset = !started;
        started = true;
        return new CodeFileDeltaMessage(
                streamId, path, delta, CodeStreamLanguageUtils.detectLanguage(path), operation, reset);
    }

    private void consume(char current) {
        if (!inString) {
            if (current == '"') {
                inString = true;
                escaped = false;
                unicodeDigitsRemaining = 0;
                token.setLength(0);
                readingKey = !expectingValue;
                activeValueKey = readingKey ? null : currentKey;
                expectingValue = false;
            } else if (current == ':' && currentKey != null) {
                expectingValue = true;
            } else if (current == ',' || current == '}') {
                currentKey = null;
                expectingValue = false;
            }
            return;
        }

        if (unicodeDigitsRemaining > 0) {
            int digit = Character.digit(current, 16);
            if (digit >= 0) {
                unicodeValue = (unicodeValue << 4) | digit;
                unicodeDigitsRemaining--;
                if (unicodeDigitsRemaining == 0) appendDecoded((char) unicodeValue);
            } else {
                unicodeDigitsRemaining = 0;
                appendDecoded(current);
            }
            return;
        }

        if (escaped) {
            escaped = false;
            switch (current) {
                case '"', '\\', '/' -> appendDecoded(current);
                case 'b' -> appendDecoded('\b');
                case 'f' -> appendDecoded('\f');
                case 'n' -> appendDecoded('\n');
                case 'r' -> appendDecoded('\r');
                case 't' -> appendDecoded('\t');
                case 'u' -> {
                    unicodeDigitsRemaining = 4;
                    unicodeValue = 0;
                }
                default -> appendDecoded(current);
            }
            return;
        }

        if (current == '\\') {
            escaped = true;
            return;
        }
        if (current == '"') {
            inString = false;
            if (readingKey) {
                currentKey = token.toString();
            } else if ("relativeFilePath".equals(activeValueKey)) {
                path = token.toString().replace('\\', '/');
                pathJustCompleted = true;
            }
            activeValueKey = null;
            return;
        }
        appendDecoded(current);
    }

    private void appendDecoded(char decoded) {
        if (readingKey || "relativeFilePath".equals(activeValueKey)) {
            token.append(decoded);
        } else if ("content".equals(activeValueKey) || "newContent".equals(activeValueKey)) {
            pendingDelta.append(decoded);
        }
    }
}
