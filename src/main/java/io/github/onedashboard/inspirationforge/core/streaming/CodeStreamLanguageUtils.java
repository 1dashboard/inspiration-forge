package io.github.onedashboard.inspirationforge.core.streaming;

public final class CodeStreamLanguageUtils {

    private CodeStreamLanguageUtils() {
    }

    public static String detectLanguage(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".vue")) return "vue";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".tsx")) return "tsx";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".jsx")) return "jsx";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        return "text";
    }
}
