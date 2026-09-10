package io.github.onedashboard.inspirationforge.runtime.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RuntimeRecordMapper extends BaseMapper<RuntimeRecord> {

    @Delete("DELETE FROM runtime_record WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
