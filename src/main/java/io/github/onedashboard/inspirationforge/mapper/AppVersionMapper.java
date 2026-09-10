package io.github.onedashboard.inspirationforge.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.model.entity.AppVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface AppVersionMapper extends BaseMapper<AppVersion> {
    @Delete("DELETE FROM app_version WHERE appId = #{appId}")
    int deletePermanentlyByAppId(@Param("appId") Long appId);
}
