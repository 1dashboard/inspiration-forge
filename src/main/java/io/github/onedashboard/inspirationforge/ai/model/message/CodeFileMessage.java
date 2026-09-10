package io.github.onedashboard.inspirationforge.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Structured file update sent to the client during a tool-driven generation. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CodeFileMessage extends StreamMessage {

    private String path;
    private String content;
    private String language;
    private String operation;
    private String summary;

    public CodeFileMessage(String path, String content, String language, String operation, String summary) {
        super("code_file");
        this.path = path;
        this.content = content;
        this.language = language;
        this.operation = operation;
        this.summary = summary;
    }
}
