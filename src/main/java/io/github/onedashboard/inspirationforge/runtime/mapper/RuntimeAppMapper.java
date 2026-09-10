package io.github.onedashboard.inspirationforge.runtime.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.runtime.model.entity.RuntimeApp;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RuntimeAppMapper extends BaseMapper<RuntimeApp> {

    @Delete("DELETE FROM runtime_app WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
