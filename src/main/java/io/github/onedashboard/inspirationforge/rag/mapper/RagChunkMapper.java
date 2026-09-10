package io.github.onedashboard.inspirationforge.rag.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.rag.model.entity.RagChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RagChunkMapper extends BaseMapper<RagChunk> {
    @Delete("DELETE FROM rag_chunk WHERE documentId = #{documentId}")
    int deletePermanentlyByDocumentId(@Param("documentId") Long documentId);

    @Delete("DELETE FROM rag_chunk WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
