package io.github.onedashboard.inspirationforge.rag.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.rag.model.entity.RagDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RagDocumentMapper extends BaseMapper<RagDocument> {
    @Select("SELECT * FROM rag_document WHERE appId = #{appId} AND sourceType = #{sourceType} AND isDelete = 0")
    List<RagDocument> selectByAppAndSourceType(@Param("appId") Long appId, @Param("sourceType") String sourceType);

    @Delete("DELETE FROM rag_document WHERE appId = #{appId} AND sourceType = #{sourceType}")
    int deletePermanentlyByAppIdAndSourceType(@Param("appId") Long appId, @Param("sourceType") String sourceType);

    @Delete("DELETE FROM rag_document WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
