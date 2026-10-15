package com.howe.share.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareAccessLog;
import com.howe.share.domain.ShareLink;
import com.howe.share.domain.dto.ShareLinkCreateRequest;
import com.howe.share.domain.dto.ShareLinkUpdateRequest;
import com.howe.share.domain.vo.WebDavEntry;
import com.howe.share.mapper.ShareAccessLogMapper;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.route.ShareRouteCache;
import com.howe.share.service.IShareLinkService;
import com.howe.share.service.IShareSourceService;
import com.howe.share.service.ShareCacheService;
import com.howe.share.storage.ShareStorageRouter;
import com.howe.share.storage.ShareStorageService;
import com.howe.share.webdav.ShareWebDavClient;
import com.howe.share.webdav.WebDavAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 分享记录 Service 实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkServiceImpl implements IShareLinkService {

    /** Base62 字符集 */
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 短码长度 */
    private static final int CODE_LENGTH = 8;

    /** 短码生成最大尝试次数 */
    private static final int CODE_MAX_ATTEMPTS = 5;

    /** 单文件下载上限默认值（50M） */
    private static final long DEFAULT_MAX_SIZE = 52428800L;

    /** 短链基址默认值（与后端 /s/{code} 路由一致，末尾带 /） */
    private static final String DEFAULT_BASE_URL = "https://s.wyantao.com/s/";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkMapper shareLinkMapper;

    private final ShareAccessLogMapper shareAccessLogMapper;

    private final IShareSourceService shareSourceService;

    private final ShareWebDavClient webDavClient;

    private final ShareStorageRouter storageRouter;

    private final ShareStorageService storageService;

    private final ShareRouteCache routeCache;

    private final ShareCacheService cacheService;

    @Override
    public List<ShareLink> selectShareLinkList(ShareLink shareLink) {
        List<ShareLink> list = shareLinkMapper.selectShareLinkList(shareLink);
        list.forEach(link -> {
            link.setPasswordSet(StrUtil.isNotBlank(link.getPasswordHash()));
            link.setPasswordHash(null);
            link.setShortUrl(shortUrl(link.getCode()));
        });
        return list;
    }

    @Override
    public ShareLink selectShareLinkById(Long linkId) {
        ShareLink link = shareLinkMapper.selectShareLinkById(linkId);
        if (link != null) {
            link.setPasswordSet(StrUtil.isNotBlank(link.getPasswordHash()));
            link.setPasswordHash(null);
            link.setShortUrl(shortUrl(link.getCode()));
        }
        return link;
    }

    @Override
    public ShareLink selectShareLinkByCode(String code) {
        return shareLinkMapper.selectShareLinkByCode(code);
    }

    @Override
    public ShareLink create(ShareLinkCreateRequest request, String createBy) {
        if (request.sourceId() == null) {
            throw new ServiceException("源不能为空");
        }
        assertSafePath(request.filePath());
        WebDavAuth auth = shareSourceService.authOf(request.sourceId());
        WebDavEntry entry = webDavClient.stat(auth, request.filePath());
        if (entry == null) {
            throw new ServiceException("文件不存在或无权访问：" + request.filePath());
        }
        if (entry.directory()) {
            throw new ServiceException("不能分享目录，请选择文件");
        }
        long maxSize = ConfigUtils.getInt(ConfigConstants.SHARE_DOWNLOAD_MAX_SIZE, (int) DEFAULT_MAX_SIZE);
        if (entry.size() > maxSize) {
            throw new ServiceException("文件超过分享上限（" + maxSize + " 字节）");
        }
        ShareLink link = new ShareLink();
        link.setCode(generateCode());
        link.setSourceId(request.sourceId());
        link.setFilePath(request.filePath());
        link.setFileName(StrUtil.blankToDefault(entry.name(), fileNameOf(request.filePath())));
        link.setFileExt(ShareStorageRouter.extOf(link.getFileName()));
        link.setFileSize(entry.size());
        link.setContentType(entry.contentType());
        link.setStorageType(storageRouter.route(entry.size(), entry.contentType(), link.getFileName()));
        link.setStorageLocation("");
        link.setPasswordHash(StrUtil.isBlank(request.password()) ? "" : SecurityUtils.encryptPassword(request.password()));
        link.setExpireAt(request.expireAt());
        link.setMaxViews(request.maxViews() == null ? 1 : request.maxViews());
        link.setViewCount(0);
        link.setBurnAfterRead(StrUtil.isBlank(request.burnAfterRead()) ? ShareConstants.BURN_NO : request.burnAfterRead());
        link.setCacheStatus(ShareConstants.CACHE_PENDING);
        link.setCacheError("");
        link.setCacheRetryCount(0);
        link.setStatus(ShareConstants.STATUS_NORMAL);
        link.setCreateBy(createBy);
        link.setRemark(request.remark());
        shareLinkMapper.insertShareLink(link);
        // 立即返回短链，字节异步缓存
        cacheService.submit(link.getLinkId());
        link.setPasswordSet(StrUtil.isNotBlank(link.getPasswordHash()));
        link.setPasswordHash(null);
        link.setShortUrl(shortUrl(link.getCode()));
        return link;
    }

    @Override
    @Transactional
    public int update(ShareLinkUpdateRequest request, String updateBy) {
        ShareLink existing = shareLinkMapper.selectShareLinkById(request.linkId());
        if (existing == null) {
            throw new ServiceException("分享不存在");
        }
        ShareLink patch = new ShareLink();
        patch.setLinkId(request.linkId());
        // 密码：null 不改动；空串清除；非空设为该密码
        if (request.password() != null) {
            patch.setPasswordHash(StrUtil.isBlank(request.password())
                    ? "" : SecurityUtils.encryptPassword(request.password()));
        }
        patch.setExpireAt(request.expireAt());
        patch.setMaxViews(request.maxViews());
        patch.setBurnAfterRead(request.burnAfterRead());
        patch.setRemark(request.remark());
        patch.setUpdateBy(updateBy);
        int rows = shareLinkMapper.updateShareLink(patch);
        if (StrUtil.isNotBlank(request.status()) && !request.status().equals(existing.getStatus())) {
            shareLinkMapper.updateStatus(request.linkId(), request.status());
        }
        routeCache.evict(existing.getCode());
        return rows;
    }

    @Override
    public int changeStatus(Long linkId, String status) {
        ShareLink existing = shareLinkMapper.selectShareLinkById(linkId);
        if (existing == null) {
            throw new ServiceException("分享不存在");
        }
        int rows = shareLinkMapper.updateStatus(linkId, status);
        // 恢复（手动失效 → 正常）时重置访问计数并重新入队缓存，避免恢复后仍因
        // view_count 已满或缓存失败继续返回 410 / 准备失败
        if (ShareConstants.STATUS_NORMAL.equals(status)) {
            shareLinkMapper.resetForRestore(linkId);
            cacheService.submit(linkId);
        }
        routeCache.evict(existing.getCode());
        return rows;
    }

    @Override
    public int deleteByIds(Long[] linkIds) {
        if (linkIds == null || linkIds.length == 0) {
            return 0;
        }
        // 先取记录（后面清理存储要用），再删库，最后清理缓存与存储对象
        List<ShareLink> links = new ArrayList<>();
        for (Long linkId : linkIds) {
            ShareLink link = shareLinkMapper.selectShareLinkById(linkId);
            if (link != null) {
                links.add(link);
            }
        }
        shareAccessLogMapper.deleteShareAccessLogByLinkIds(linkIds);
        int rows = shareLinkMapper.deleteShareLinkByIds(linkIds);
        for (ShareLink link : links) {
            routeCache.evict(link.getCode());
            storageService.remove(link);
        }
        return rows;
    }

    @Override
    public int recache(Long linkId) {
        ShareLink link = shareLinkMapper.selectShareLinkById(linkId);
        if (link == null) {
            throw new ServiceException("分享不存在");
        }
        int rows = shareLinkMapper.resetCacheStatus(linkId);
        routeCache.evict(link.getCode());
        cacheService.submit(linkId);
        return rows;
    }

    @Override
    public List<ShareAccessLog> selectLogs(Long linkId) {
        return shareAccessLogMapper.selectShareAccessLogByLinkId(linkId);
    }

    /**
     * 生成唯一短码（Base62，8 位，不可枚举）
     */
    private String generateCode() {
        for (int i = 0; i < CODE_MAX_ATTEMPTS; i++) {
            String code = randomCode();
            if (shareLinkMapper.selectShareLinkByCode(code) == null) {
                return code;
            }
        }
        throw new ServiceException("生成短码失败，请重试");
    }

    /**
     * 生成随机短码
     */
    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    /**
     * 拼短链地址
     */
    private String shortUrl(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        String base = ConfigUtils.getString(ConfigConstants.SHARE_BASE_URL, DEFAULT_BASE_URL);
        if (StrUtil.isBlank(base)) {
            base = DEFAULT_BASE_URL;
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + code;
    }

    /**
     * 取路径中的文件名
     */
    private String fileNameOf(String path) {
        String p = path.replace("\\", "/");
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(idx + 1) : p;
    }

    /**
     * 路径穿越校验
     */
    private void assertSafePath(String path) {
        if (StrUtil.isBlank(path)) {
            throw new ServiceException("文件路径不能为空");
        }
        if (path.contains("..")) {
            throw new ServiceException("文件路径不合法");
        }
    }
}
