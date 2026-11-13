package com.howe.share.tool.dto;

/**
 * 分享只读工具的对外数据载体。
 *
 * <p>只承载管理端可见的白名单字段：不返回短码、短链、远端地址、文件路径、
 * 存储落点、缓存错误、密码哈希或创建者信息。</p>
 */
public final class ShareReadDtos {

    /** 工具数据载体不允许实例化。 */
    private ShareReadDtos() {
    }

    /**
     * 分享视图。
     *
     * @param linkId 分享 ID
     * @param displayName 展示名（文件名）
     * @param sourceType 来源类型
     * @param status 状态，{@code enabled}/{@code disabled}/{@code burned}/{@code deleting}
     * @param passwordSet 是否设置密码
     * @param expireAt 过期时间
     * @param maxViews 最大访问次数
     * @param viewCount 已访问次数
     * @param burnAfterRead 是否阅后即焚
     * @param cacheStatus 缓存状态
     */
    public record LinkView(String linkId, String displayName, String sourceType, String status, Boolean passwordSet,
            String expireAt, Integer maxViews, Integer viewCount, Boolean burnAfterRead, String cacheStatus) {
    }
}
