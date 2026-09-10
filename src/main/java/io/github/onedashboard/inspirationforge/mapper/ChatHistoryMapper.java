package io.github.onedashboard.inspirationforge.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 对话历史 映射层。
 *
 * @author 1dashboard
 */
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    @Delete("DELETE FROM chat_history WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
