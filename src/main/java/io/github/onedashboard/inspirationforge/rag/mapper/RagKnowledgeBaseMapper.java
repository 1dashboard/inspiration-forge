package io.github.onedashboard.inspirationforge.rag.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.rag.model.entity.RagKnowledgeBase;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RagKnowledgeBaseMapper extends BaseMapper<RagKnowledgeBase> {
    @Delete("DELETE FROM rag_knowledge_base WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
