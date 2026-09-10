package io.github.onedashboard.inspirationforge.mapper;

import com.mybatisflex.core.BaseMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 应用 映射层。
 *
 * @author 1dashboard
 */
public interface AppMapper extends BaseMapper<App> {

    @Delete("DELETE FROM app WHERE id = #{id}")
    int deletePermanentlyById(@Param("id") Long id);
}
