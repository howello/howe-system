package com.howe.blog.mapper;

import com.howe.blog.domain.vo.BlogNoticePublicVo;

import java.util.List;

/**
 * 博客公开公告数据层
 *
 * @author howe
 */
public interface BlogNoticeMapper {
    /**
     * 查询已发布的博客公开公告
     *
     * @return 公告集合
     */
    List<BlogNoticePublicVo> selectPublicNoticeList();
}
