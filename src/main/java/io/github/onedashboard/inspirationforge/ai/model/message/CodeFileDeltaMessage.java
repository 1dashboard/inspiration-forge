package io.github.onedashboard.inspirationforge.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Incremental code content emitted while an AI response is still streaming. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CodeFileDeltaMessage extends StreamMessage {

    private String streamId;
    private String path;
    private String delta;
    private String language;
    private String operation;
    private boolean reset;

    public CodeFileDeltaMessage(String streamId, String path, String delta, String language,
                                String operation, boolean reset) {
        super("code_file_delta");
        this.streamId = streamId;
        this.path = path;
        this.delta = delta;
        this.language = language;
        this.operation = operation;
        this.reset = reset;
    }
}
