package com.howe.blog.task;

import com.howe.blog.service.WalineLinkSyncService;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * waline 友链申请同步任务
 *
 * <p>由 Spring {@code @Scheduled} 每 30 分钟触发一次；原 quartz 调度已随 module-quartz 移除。
 * 是否执行由参数 {@code blog.link.syncEnabled}（默认 false）控制，可在「系统管理 &gt; 参数设置」开关。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogLinkRequestTask {

    /** 每次同步的时间窗口（分钟） */
    private static final int WINDOW_MINUTES = 30;

    private final WalineLinkSyncService walineLinkSyncService;

    /**
     * 定时同步（每 30 分钟），窗口固定 30 分钟；开关关闭时跳过
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void scheduledSync() {
        if (!ConfigUtils.getBoolean(ConfigConstants.BLOG_LINK_SYNC_ENABLED, false)) {
            log.debug("友链同步未启用（{}），跳过本次调度", ConfigConstants.BLOG_LINK_SYNC_ENABLED);
            return;
        }
        sync(WINDOW_MINUTES);
    }

    /**
     * 同步友链申请留言
     *
     * @param windowMinutes 时间窗口（分钟）
     */
    public void sync(Integer windowMinutes) {
        log.info("开始同步 waline 友链申请留言，窗口={}分钟", windowMinutes);
        WalineLinkSyncService.WalineLinkSyncResult result = walineLinkSyncService.sync(windowMinutes);
        log.info("waline 友链同步完成：新增{}条，跳过{}条，翻{}页",
                result.newCount(), result.skipCount(), result.pageCount());
    }
}
