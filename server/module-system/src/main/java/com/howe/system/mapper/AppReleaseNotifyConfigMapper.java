package com.howe.system.mapper;

import com.howe.system.domain.AppReleaseNotifyConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** App 发版机器人配置数据访问。 */
public interface AppReleaseNotifyConfigMapper {

    /** 查询应用的全部机器人，或只查已启用的机器人。 */
    List<AppReleaseNotifyConfig> selectByApp(@Param("appKey") String appKey,
            @Param("enabledOnly") boolean enabledOnly);

    /** 保存应用的新机器人配置。 */
    int insert(@Param("appKey") String appKey, @Param("config") AppReleaseNotifyConfig config);

    /** 按应用及配置 ID 更新机器人配置。 */
    int update(@Param("appKey") String appKey, @Param("configId") Long configId,
            @Param("config") AppReleaseNotifyConfig config);

    /** 按应用及配置 ID 删除机器人配置。 */
    int delete(@Param("appKey") String appKey, @Param("configId") Long configId);
}
