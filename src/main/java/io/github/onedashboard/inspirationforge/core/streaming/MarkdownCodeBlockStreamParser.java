package io.github.onedashboard.inspirationforge.core.streaming;

import io.github.onedashboard.inspirationforge.ai.model.message.CodeFileDeltaMessage;
import io.github.onedashboard.inspirationforge.model.enums.CodeGenTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits streamed Markdown code fences into incremental file events. */
public class MarkdownCodeBlockStreamParser {

    private static final Pattern FENCE_HEADER = Pattern.compile("```(html|css|js|javascript)\\s*\\r?\\n",
            Pattern.CASE_INSENSITIVE);

    private final CodeGenTypeEnum codeGenType;
    private final StringBuilder buffer = new StringBuilder();
    private String currentPath;
    private String currentLanguage;
    private boolean firstDelta;

    public MarkdownCodeBlockStreamParser(CodeGenTypeEnum codeGenType) {
        this.codeGenType = codeGenType;
    }

    public List<CodeFileDeltaMessage> accept(String chunk) {
        if (chunk != null) buffer.append(chunk);
        return drain(false);
    }

    public List<CodeFileDeltaMessage> finish() {
        return drain(true);
    }

    private List<CodeFileDeltaMessage> drain(boolean finishing) {
        List<CodeFileDeltaMessage> messages = new ArrayList<>();
        while (true) {
            if (currentPath == null) {
                Matcher matcher = FENCE_HEADER.matcher(buffer);
                if (!matcher.find()) {
                    if (!finishing && buffer.length() > 32) {
                        buffer.delete(0, buffer.length() - 32);
                    }
                    break;
                }
                String language = matcher.group(1).toLowerCase(Locale.ROOT);
                currentPath = resolvePath(language);
                currentLanguage = "javascript".equals(language) ? "javascript" : language;
                firstDelta = true;
                buffer.delete(0, matcher.end());
            }

            int closingFence = buffer.indexOf("```");
            if (closingFence >= 0) {
                emit(messages, buffer.substring(0, closingFence));
                buffer.delete(0, closingFence + 3);
                currentPath = null;
                currentLanguage = null;
                continue;
            }

            int safeLength = finishing ? buffer.length() : Math.max(0, buffer.length() - 2);
            if (safeLength > 0) {
                emit(messages, buffer.substring(0, safeLength));
                buffer.delete(0, safeLength);
            }
            break;
        }
        return messages;
    }

    private void emit(List<CodeFileDeltaMessage> messages, String delta) {
        if (delta.isEmpty() && !firstDelta) return;
        messages.add(new CodeFileDeltaMessage(
                "markdown:" + currentPath, currentPath, delta, currentLanguage, "write", firstDelta));
        firstDelta = false;
    }

    private String resolvePath(String language) {
        if (codeGenType == CodeGenTypeEnum.HTML) return "index.html";
        return switch (language) {
            case "css" -> "style.css";
            case "js", "javascript" -> "script.js";
            default -> "index.html";
        };
    }
}
