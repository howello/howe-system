package com.howe.system.tool.dto;

import java.util.List;

/**
 * 系统只读工具的对外数据载体。
 *
 * <p>公告正文只返回纯文本摘要，不输出原始 HTML、创建者或内部状态码。</p>
 */
public final class SystemReadDtos {

    /** 工具数据载体不允许实例化。 */
    private SystemReadDtos() {
    }

    /**
     * 公告列表项。
     *
     * @param noticeId 公告 ID
     * @param title 标题
     * @param type 类型
     * @param createdAt 创建时间
     * @param isRead 当前用户是否已读
     * @param summary 正文纯文本摘要
     */
    public record NoticeItem(String noticeId, String title, String type, String createdAt, boolean isRead,
            String summary) {
    }

    /**
     * 公告列表结果。
     *
     * @param items 当前页公告
     * @param unreadCount 当前用户未读总数，不受已读筛选影响
     */
    public record NoticePage(List<NoticeItem> items, Integer unreadCount) {
    }
}
