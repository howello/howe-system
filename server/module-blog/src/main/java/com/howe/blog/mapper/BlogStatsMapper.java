package com.howe.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 博客统计 数据层
 *
 * <p>只做基础聚合取数，日期窗口过滤、补 0、Top7+其他 等口径在
 * {@code BlogStatsServiceImpl} 处理，本 Mapper 不放业务逻辑。</p>
 *
 * @author howe
 */
@Mapper
public interface BlogStatsMapper {
    /** 三总计数：键 article_total / draft_total / talk_total */
    Map<String, Object> selectTotalCounts();

    /** 文章按发布日计数（含隐藏；publish_date 为 null 已过滤） */
    List<Map<String, Object>> selectArticleCountByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 草稿按创建日计数（仅 status='0'） */
    List<Map<String, Object>> selectDraftCountByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 说说按创建日计数（全量） */
    List<Map<String, Object>> selectTalkCountByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 全量文章分类列（逗号分隔的原始串），供服务层拆分聚合 */
    List<String> selectAllArticleCategories();

    /**
     * 订阅源总数（link_type='2'，排除停用）
     *
     * @return 订阅源总数
     */
    long selectFeedTotal();

    /**
     * 同步失败的订阅源数（last_error 非空）
     *
     * @return 失败数
     */
    long selectFeedFailedCount();

    /**
     * 最近一次同步时间（订阅源中最大的 last_sync_time）
     *
     * @return 最近同步时间；从未同步时返回 null
     */
    java.util.Date selectLatestFeedSyncTime();

    /**
     * 同步失败的订阅源明细（按最近同步时间倒序）
     *
     * @param limit 取前 N 条
     * @return 每行含 link_name 与 last_error
     */
    List<Map<String, Object>> selectFeedFailures(@Param("limit") int limit);

    /**
     * 待办：未发布草稿数（status='0'）
     *
     * @return 未发布草稿数
     */
    long selectUnpublishedDraftCount();

    /**
     * 个人足迹：指定创建者的文章数
     *
     * @param createBy 创建者
     * @return 文章数
     */
    long selectArticleCountByCreator(@Param("createBy") String createBy);
}
