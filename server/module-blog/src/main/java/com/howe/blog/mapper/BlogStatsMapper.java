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
}
