package com.howe.share.task;

import com.howe.share.service.ShareCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分享缓存补偿任务
 *
 * <p>定时扫「待缓存 / 缓存失败且未超重试上限」的记录重新入队，兜住创建时异步任务被拒或失败的情况。
 * 开关为 {@code share.cache.retryEnabled}。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareCacheRetryTask {

    private final ShareCacheService cacheService;

    /**
     * 补偿缓存（每分钟一次；开关关闭时跳过）
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 45000)
    public void retry() {
        try {
            cacheService.retryPending();
        } catch (Exception e) {
            log.warn("分享缓存补偿任务异常", e);
        }
    }
}
