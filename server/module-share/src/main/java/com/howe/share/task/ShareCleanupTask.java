package com.howe.share.task;

import cn.hutool.core.io.FileUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareLink;
import com.howe.share.mapper.ShareAccessLogMapper;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.route.ShareRouteCache;
import com.howe.share.storage.ShareStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 分享定时清理任务
 *
 * <p>清理已焚毁 / 已过期的分享：删路由缓存与存储对象并置为已失效；清理本地缓存目录中的无主残留文件；
 * 按保留天数清理访问日志。单批上限 + 逐条失败隔离，避免一次调度占用过久。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareCleanupTask {

    /** 单批最多处理条数 */
    private static final int BATCH = 50;

    /** 访问日志保留天数默认值 */
    private static final int DEFAULT_RETAIN_DAYS = 30;

    private final ShareLinkMapper shareLinkMapper;

    private final ShareAccessLogMapper shareAccessLogMapper;

    private final ShareRouteCache routeCache;

    private final ShareStorageService storageService;

    /**
     * 清理任务（每 10 分钟一次）
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 60000)
    public void cleanup() {
        cleanupLinks();
        cleanupOrphanFiles();
        cleanupLogs();
    }

    /**
     * 清理已焚毁 / 已过期的分享记录
     */
    private void cleanupLinks() {
        List<ShareLink> list = shareLinkMapper.selectCleanupList(BATCH);
        for (ShareLink link : list) {
            try {
                routeCache.evict(link.getCode());
                storageService.remove(link);
                shareLinkMapper.updateStatus(link.getLinkId(), ShareConstants.STATUS_INVALID);
                log.info("清理分享：linkId={}, code={}", link.getLinkId(), link.getCode());
            } catch (Exception e) {
                log.warn("清理分享失败：linkId={}", link.getLinkId(), e);
            }
        }
    }

    /**
     * 清理本地缓存目录中无主的短码子目录
     */
    private void cleanupOrphanFiles() {
        try {
            File shareDir = storageService.shareLocalDir();
            if (!shareDir.exists() || !shareDir.isDirectory()) {
                return;
            }
            Set<String> codes = new HashSet<>(shareLinkMapper.selectAllCodes());
            File[] children = shareDir.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                if (child.isDirectory() && !codes.contains(child.getName())) {
                    FileUtil.del(child);
                    log.info("清理无主分享缓存目录：{}", child.getName());
                }
            }
        } catch (Exception e) {
            log.warn("清理本地残留分享文件失败", e);
        }
    }

    /**
     * 清理超期访问日志
     */
    private void cleanupLogs() {
        try {
            int retainDays = Math.max(1,
                    ConfigUtils.getInt(ConfigConstants.SHARE_ACCESS_LOG_RETAIN_DAYS, DEFAULT_RETAIN_DAYS));
            Date before = new Date(System.currentTimeMillis() - retainDays * 86400000L);
            int rows = shareAccessLogMapper.deleteShareAccessLogBefore(before);
            if (rows > 0) {
                log.info("清理分享访问日志 {} 条（保留 {} 天）", rows, retainDays);
            }
        } catch (Exception e) {
            log.warn("清理分享访问日志失败", e);
        }
    }
}
