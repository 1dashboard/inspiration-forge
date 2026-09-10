package io.github.onedashboard.inspirationforge.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AiAppNameService {
    @SystemMessage("你是应用命名助手。根据用户的应用需求生成一个简洁、自然、容易理解的中文应用名称。只输出名称本身，不要引号、标点、解释或换行，长度不超过20个字符。")
    String generateName(@UserMessage String prompt);
}
