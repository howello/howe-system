package com.howe.blog.task;

import com.howe.blog.domain.vo.BlogFeedSyncResult;
import com.howe.blog.service.IBlogFeedSyncService;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 博客朋友圈 RSS 同步任务
 *
 * <p>由 Spring {@code @Scheduled} 每 2 小时触发一次；原 quartz 调度已随 module-quartz 移除。
 * 是否执行由参数 {@code blog.feed.syncEnabled}（默认 false）控制，可在「系统管理 &gt; 参数设置」开关。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogFeedTask {

    private final IBlogFeedSyncService blogFeedSyncService;

    /**
     * 同步全部启用的订阅源（每 2 小时，整点触发；开关关闭时跳过）
     */
    @Scheduled(cron = "0 0 */2 * * ?")
    public void sync() {
        if (!ConfigUtils.getBoolean(ConfigConstants.BLOG_FEED_SYNC_ENABLED, false)) {
            log.debug("朋友圈同步未启用（{}），跳过本次调度", ConfigConstants.BLOG_FEED_SYNC_ENABLED);
            return;
        }
        BlogFeedSyncResult result = blogFeedSyncService.syncAll();
        log.info("朋友圈同步完成：共{}个源，成功{}，失败{}，新增{}条",
                result.total(), result.success(), result.failed(), result.newItems());
    }
}
