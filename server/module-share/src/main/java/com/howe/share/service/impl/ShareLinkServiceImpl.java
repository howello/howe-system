package com.howe.share.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.http.RemoteFileGuard;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareAccessLog;
import com.howe.share.domain.ShareLink;
import com.howe.share.domain.ShareUpload;
import com.howe.share.domain.dto.ShareLinkCreateRequest;
import com.howe.share.domain.dto.ShareLinkUpdateRequest;
import com.howe.share.domain.vo.WebDavEntry;
import com.howe.share.mapper.ShareAccessLogMapper;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.mapper.ShareUploadMapper;
import com.howe.share.route.ShareRouteCache;
import com.howe.share.service.IShareLinkService;
import com.howe.share.service.IShareSourceService;
import com.howe.share.service.ShareCacheService;
import com.howe.share.service.ShareUploadService;
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

    private final ShareUploadService shareUploadService;

    private final ShareUploadMapper shareUploadMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public ShareLink create(ShareLinkCreateRequest request, String createBy) {
        String sourceType = request.normalizedSourceType();
        ShareLink link = new ShareLink();
        link.setSourceType(sourceType);

        if (ShareConstants.SOURCE_LOCAL.equals(sourceType)) {
            buildLocalSource(link, request);
        } else if (ShareConstants.SOURCE_REMOTE.equals(sourceType)) {
            buildRemoteSource(link, request);
        } else {
            buildWebDavSource(link, request);
        }

        link.setCode(generateCode());
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

        // 本地来源的认领放在插入之后：认领需要 linkId，且两者在同一事务内，
        // 任一步失败都会回滚，暂存仍保持 TEMP 可重试
        if (ShareConstants.SOURCE_LOCAL.equals(sourceType)) {
            shareUploadService.claim(request.uploadToken(), SecurityUtils.getUserId(), link.getLinkId());
        }

        // 立即返回短链，字节异步缓存（事务提交后由缓存任务读取）
        cacheService.submit(link.getLinkId());
        link.setPasswordSet(StrUtil.isNotBlank(link.getPasswordHash()));
        link.setPasswordHash(null);
        link.setShortUrl(shortUrl(link.getCode()));
        return link;
    }

    /**
     * 按 WebDAV 来源填充分享记录
     *
     * <p>既有行为完全保留：stat 取元数据、校验存在与大小、按路由决定存储落点。</p>
     *
     * @param link    分享记录
     * @param request 创建请求
     */
    private void buildWebDavSource(ShareLink link, ShareLinkCreateRequest request) {
        if (request.sourceId() == null) {
            throw new ServiceException("WebDAV 来源必须选择源");
        }
        if (StrUtil.isBlank(request.filePath())) {
            throw new ServiceException("WebDAV 来源必须选择文件");
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
        link.setSourceId(request.sourceId());
        link.setFilePath(request.filePath());
        link.setFileName(StrUtil.blankToDefault(entry.name(), fileNameOf(request.filePath())));
        link.setFileExt(ShareStorageRouter.extOf(link.getFileName()));
        link.setFileSize(entry.size());
        link.setContentType(entry.contentType());
        link.setStorageType(storageRouter.route(entry.size(), entry.contentType(), link.getFileName()));
        link.setStorageLocation("");
    }

    /**
     * 按本地上传来源填充分享记录
     *
     * <p>文件名/大小来自暂存记录，不信任客户端传的值。</p>
     *
     * @param link    分享记录
     * @param request 创建请求
     */
    private void buildLocalSource(ShareLink link, ShareLinkCreateRequest request) {
        if (StrUtil.isBlank(request.uploadToken())) {
            throw new ServiceException("本地上传来源必须提供上传令牌");
        }
        // 先按令牌取记录做预校验（真正的认领在插入后，避免占用后因插入失败而丢失）
        ShareUpload upload = shareUploadMapper.selectByTokenHash(
                cn.hutool.crypto.digest.DigestUtil.sha256Hex(request.uploadToken().trim()));
        if (upload == null || !ShareConstants.UPLOAD_TEMP.equals(upload.getState())) {
            throw new ServiceException("上传记录不存在或已被使用");
        }
        if (!upload.getOwnerUserId().equals(SecurityUtils.getUserId())) {
            throw new ServiceException("该上传不属于当前用户");
        }
        long maxSize = ConfigUtils.getInt(ConfigConstants.SHARE_DOWNLOAD_MAX_SIZE, (int) DEFAULT_MAX_SIZE);
        if (upload.getFileSize() != null && upload.getFileSize() > maxSize) {
            throw new ServiceException("文件超过分享上限（" + maxSize + " 字节）");
        }
        link.setFileName(upload.getFileName());
        link.setFileExt(upload.getFileExt());
        link.setFileSize(upload.getFileSize());
        link.setContentType(upload.getContentType());
        link.setStorageType(storageRouter.route(upload.getFileSize(), upload.getContentType(), upload.getFileName()));
        link.setStorageLocation("");
        link.setLocalUploadId(upload.getUploadId());
    }

    /**
     * 按远程链接来源填充分享记录
     *
     * <p>创建前做一次可达性与安全校验：明确不可达时直接拒绝，避免用户拿到一个注定失败的短链。
     * 创建后的临时网络失败进入既有缓存失败状态。</p>
     *
     * @param link    分享记录
     * @param request 创建请求
     */
    private void buildRemoteSource(ShareLink link, ShareLinkCreateRequest request) {
        if (StrUtil.isBlank(request.remoteUrl())) {
            throw new ServiceException("远程链接来源必须填写地址");
        }
        java.net.URI uri = RemoteFileGuard.validate(request.remoteUrl());
        link.setRemoteUrl(request.remoteUrl().trim());
        // 文件名从 URL 路径推断并清洗；大小等字节真正拉取后才知道
        String fileName = StrUtil.isBlank(uri.getPath()) ? "" : fileNameOf(uri.getPath());
        link.setFileName(StrUtil.blankToDefault(fileName, "remote-file"));
        link.setFileExt(ShareStorageRouter.extOf(link.getFileName()));
        link.setFileSize(0L);
        link.setContentType("");
        // 远程来源默认落 R2：大小未知，避免把大文件写到本机磁盘
        link.setStorageType(ShareConstants.STORAGE_R2);
        link.setStorageLocation("");
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
        // 先取记录（后面清理存储要用），再删库，最后清理缓存与存储对象。
        // 注意：存储对象删除失败时不能连记录一起丢——否则对象位置永久丢失，
        // 只能靠存储层扫全量目录碰运气。这里先删库是既有实现，其余失败由
        // storageService.remove 内部记日志，定时任务按已删记录无法回收。
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
            // 本地上传来源：分享已删，暂存不再需要
            if (ShareConstants.SOURCE_LOCAL.equals(link.getSourceType()) && link.getLocalUploadId() != null) {
                shareUploadService.markBound(link.getLocalUploadId());
            }
        }
        return rows;
    }

    @Override
    public int recache(Long linkId) {
        ShareLink link = shareLinkMapper.selectShareLinkById(linkId);
        if (link == null) {
            throw new ServiceException("分享不存在");
        }
        // 本地来源的暂存字节在缓存成功后会被删掉，没有可重复拉取的上游。
        // 最终对象还在就直接复用；不在则明确提示重建分享，不能静默失败。
        if (ShareConstants.SOURCE_LOCAL.equals(link.getSourceType())) {
            ShareUpload upload = shareUploadService.selectById(link.getLocalUploadId());
            boolean stagingAlive = upload != null
                    && !ShareConstants.UPLOAD_BOUND.equals(upload.getState())
                    && !ShareConstants.UPLOAD_EXPIRED.equals(upload.getState());
            boolean finalObjectAlive = ShareConstants.CACHE_READY.equals(link.getCacheStatus())
                    && StrUtil.isNotBlank(link.getStorageLocation());
            if (!stagingAlive && !finalObjectAlive) {
                throw new ServiceException("本地文件分享的源文件已清理，请重新创建分享");
            }
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
