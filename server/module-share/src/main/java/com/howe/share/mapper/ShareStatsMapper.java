package com.howe.share.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分享首页统计 数据层
 *
 * <p>分享是平台级数据（不分家庭），统计直接查全表；只做基础聚合取数，
 * 状态归类与展示口径在 Service 层处理。</p>
 *
 * @author howe
 */
@Mapper
public interface ShareStatsMapper {

    /**
     * 链接总数（未删除）
     *
     * @return 总数
     */
    long selectLinkTotal();

    /**
     * 链接按状态计数
     *
     * @return 每行含 status 与 cnt
     */
    List<Map<String, Object>> selectLinkCountByStatus();

    /**
     * 缓存状态计数
     *
     * @return 每行含 cache_status 与 cnt
     */
    List<Map<String, Object>> selectLinkCountByCacheStatus();

    /**
     * 指定动作的访问次数（仅成功记录）
     *
     * @param actions 动作列表（如 detail / open / file / download）
     * @param startDay 起始日期 yyyy-MM-dd（含）
     * @param endDay   结束日期 yyyy-MM-dd（含）
     * @return 次数
     */
    long selectAccessCountByActions(@Param("actions") List<String> actions,
                                    @Param("startDay") String startDay,
                                    @Param("endDay") String endDay);

    /**
     * 最近创建的链接
     *
     * @param limit 取前 N 条
     * @return 每行含 file_name 与 create_time，按创建时间倒序
     */
    List<Map<String, Object>> selectRecentLinks(@Param("limit") int limit);

    /**
     * 待办：即将过期的正常链接数
     *
     * @param before 过期时间上界（早于该时间即视为「即将过期」）
     * @return 数量
     */
    long selectExpiringSoonCount(@Param("before") java.util.Date before);

    /**
     * 个人足迹：我创建的分享数
     *
     * @param createBy 创建者用户名
     * @return 分享数
     */
    long selectMyLinkCount(@Param("createBy") String createBy);
}
