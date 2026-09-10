package io.github.onedashboard.inspirationforge.runtime.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RuntimeModelMapper extends BaseMapper<RuntimeModel> {

    @Delete("DELETE FROM runtime_model WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
