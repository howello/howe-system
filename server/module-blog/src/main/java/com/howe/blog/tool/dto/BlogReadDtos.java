package com.howe.blog.tool.dto;

import java.util.List;

/**
 * 博客只读工具的对外数据载体。
 *
 * <p>本类型只承载工具栏已白名单字段，不含正文以外的路径、Git 凭据或创建者信息。
 * 全部字段在投影时显式构造，禁止直接把领域实体序列化给客户端。</p>
 */
public final class BlogReadDtos {

    /** 工具数据载体不允许实例化。 */
    private BlogReadDtos() {
    }

    /**
     * 文章详情。
     *
     * @param articleId 文章 ID，字符串形式
     * @param slug 文章标识
     * @param title 标题
     * @param categories 分类
     * @param tags 标签
     * @param publishDate 发布日期
     * @param wordCount 字数
     * @param recommend 是否推荐
     * @param hide 是否隐藏
     * @param isTop 是否置顶
     * @param gitSha 本次读取到的远端 SHA；未读取正文时为本地索引值
     * @param content 正文；{@code includeContent=false} 时为 null
     * @param contentTruncated 正文是否被截断
     */
    public record ArticleView(String articleId, String slug, String title, List<String> categories, List<String> tags,
            String publishDate, Integer wordCount, boolean recommend, boolean hide, boolean isTop, String gitSha,
            String content, boolean contentTruncated) {
    }

    /**
     * 草稿列表项。
     *
     * @param draftId 草稿 ID
     * @param title 标题
     * @param slug 标识
     * @param status 状态，{@code unpublished} 或 {@code published}
     */
    public record DraftSummary(String draftId, String title, String slug, String status) {
    }

    /**
     * 草稿详情。
     *
     * @param draftId 草稿 ID
     * @param title 标题
     * @param slug 标识
     * @param status 状态
     * @param content 正文，可能被截断
     * @param contentTruncated 正文是否被截断
     * @param categories 分类
     * @param tags 标签
     */
    public record DraftDetail(String draftId, String title, String slug, String status, String content,
            boolean contentTruncated, List<String> categories, List<String> tags) {
    }

    /**
     * 说说列表项。
     *
     * @param talkId 说说 ID
     * @param content 正文摘要
     * @param tags 标签
     * @param pubDate 发布时间
     * @param isTop 是否置顶
     * @param status 状态，{@code published} 或 {@code hidden}
     */
    public record TalkSummary(String talkId, String content, List<String> tags, String pubDate, boolean isTop,
            String status) {
    }

    /**
     * 说说详情。
     *
     * @param talkId 说说 ID
     * @param content 正文，可能被截断
     * @param tags 标签
     * @param pubDate 发布时间
     * @param isTop 是否置顶
     * @param status 状态
     */
    public record TalkDetail(String talkId, String content, List<String> tags, String pubDate, boolean isTop,
            String status) {
    }
}
