package com.howe.share.service;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.share.config.ShareCacheExecutorConfig;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareLink;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.route.ShareRouteCache;
import com.howe.share.storage.ShareStorageService;
import com.howe.share.webdav.ShareWebDavClient;
import com.howe.share.webdav.WebDavAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 分享文件异步缓存
 *
 * <p>创建分享后异步把字节从 WebDAV 预取到本地 / R2：领取用 {@code cache_status} 条件更新保证幂等，
 * 失败记录原因与重试次数，由 {@link com.howe.share.task.ShareCacheRetryTask} 兜底重试。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class ShareCacheService {

    /** 单文件下载上限默认值（50M） */
    private static final long DEFAULT_MAX_SIZE = 52428800L;

    /** 单批重试上限 */
    private static final int RETRY_BATCH = 20;

    private final ShareLinkMapper shareLinkMapper;

    private final IShareSourceService shareSourceService;

    private final ShareWebDavClient webDavClient;

    private final ShareStorageService storageService;

    private final ShareRouteCache routeCache;

    private final ThreadPoolTaskExecutor executor;

    /**
     * 构造缓存服务
     *
     * @param shareLinkMapper   分享记录数据层
     * @param shareSourceService 源服务
     * @param webDavClient      WebDAV 客户端
     * @param storageService    存储门面
     * @param routeCache        路由缓存
     * @param executor          分享专用线程池（按 Bean 名注入，避开框架共享池）
     */
    public ShareCacheService(ShareLinkMapper shareLinkMapper, IShareSourceService shareSourceService,
                             ShareWebDavClient webDavClient, ShareStorageService storageService,
                             ShareRouteCache routeCache,
                             @Qualifier(ShareCacheExecutorConfig.EXECUTOR_BEAN) ThreadPoolTaskExecutor executor) {
        this.shareLinkMapper = shareLinkMapper;
        this.shareSourceService = shareSourceService;
        this.webDavClient = webDavClient;
        this.storageService = storageService;
        this.routeCache = routeCache;
        this.executor = executor;
    }

    /**
     * 提交异步缓存任务
     *
     * @param linkId 分享ID
     */
    public void submit(Long linkId) {
        if (linkId == null) {
            return;
        }
        executor.execute(() -> {
            try {
                cache(linkId);
            } catch (Exception e) {
                log.warn("异步缓存任务异常：linkId={}", linkId, e);
            }
        });
    }

    /**
     * 执行单条缓存：领取 → 下载 → 落存储 → 写路由 → 置就绪
     *
     * @param linkId 分享ID
     */
    public void cache(Long linkId) {
        int claimed = shareLinkMapper.updateCacheStatus(linkId, ShareConstants.CACHE_PENDING, ShareConstants.CACHE_RUNNING);
        if (claimed == 0) {
            claimed = shareLinkMapper.updateCacheStatus(linkId, ShareConstants.CACHE_FAILED, ShareConstants.CACHE_RUNNING);
        }
        if (claimed == 0) {
            // 已被其它任务领取或已就绪，跳过
            return;
        }
        ShareLink link = shareLinkMapper.selectShareLinkById(linkId);
        if (link == null) {
            return;
        }
        try {
            WebDavAuth auth = shareSourceService.authOf(link.getSourceId());
            byte[] bytes;
            try (InputStream in = webDavClient.open(auth, link.getFilePath())) {
                bytes = readLimited(in, maxSize());
            }
            String location = storageService.store(link, bytes);
            shareLinkMapper.markCacheReady(linkId, link.getStorageType(), location, (long) bytes.length, link.getContentType());
            link.setStorageLocation(location);
            link.setFileSize((long) bytes.length);
            routeCache.put(link);
            log.info("分享文件缓存就绪：linkId={}, storage={}, size={}", linkId, link.getStorageType(), bytes.length);
        } catch (Exception e) {
            log.warn("分享文件缓存失败：linkId={}，原因：{}", linkId, e.getMessage());
            shareLinkMapper.markCacheFailed(linkId, truncate(e.getMessage()));
        }
    }

    /**
     * 兜底重试：扫描待缓存 / 失败且未超重试上限的记录重新入队
     */
    public void retryPending() {
        if (!ConfigUtils.getBoolean(ConfigConstants.SHARE_CACHE_RETRY_ENABLED, true)) {
            return;
        }
        int maxRetry = Math.max(1, ConfigUtils.getInt(ConfigConstants.SHARE_CACHE_MAX_RETRY, 3));
        List<ShareLink> list = shareLinkMapper.selectCacheRetryList(maxRetry, RETRY_BATCH);
        for (ShareLink link : list) {
            try {
                cache(link.getLinkId());
            } catch (Exception e) {
                log.warn("缓存补偿任务异常：linkId={}", link.getLinkId(), e);
            }
        }
    }

    /**
     * 读取字节流并限制大小上限
     *
     * @param in      输入流
     * @param maxSize 上限（字节）
     * @return 字节内容
     * @throws IOException IO 异常
     */
    private byte[] readLimited(InputStream in, long maxSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxSize) {
                throw new ServiceException("文件超过下载上限（" + maxSize + " 字节）");
            }
            out.write(buffer, 0, n);
        }
        IoUtil.close(in);
        return out.toByteArray();
    }

    /**
     * 下载上限（字节）
     */
    private long maxSize() {
        return ConfigUtils.getInt(ConfigConstants.SHARE_DOWNLOAD_MAX_SIZE, (int) DEFAULT_MAX_SIZE);
    }

    /**
     * 截断错误信息，避免超出列宽
     */
    private String truncate(String message) {
        if (StrUtil.isBlank(message)) {
            return "未知错误";
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
