package com.howe.share.service;

import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.storage.StoredObject;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.ip.IpUtils;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareAccessLog;
import com.howe.share.domain.ShareLink;
import com.howe.share.mapper.ShareAccessLogMapper;
import com.howe.share.mapper.ShareLinkMapper;
import com.howe.share.domain.vo.ShareOpenResult;
import com.howe.share.domain.vo.SharePublicDetail;
import com.howe.share.route.ShareRoute;
import com.howe.share.route.ShareRouteCache;
import com.howe.share.storage.ShareStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 公开端分享访问 Service
 *
 * <p>链接状态与查看次数以 MySQL 为准；路由缓存只用于加速会话内多次读取字节。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharePublicService {

    /** 密码尝试上限默认值 */
    private static final int DEFAULT_PWD_MAX_ATTEMPTS = 5;

    /** 密码尝试窗口默认值（秒） */
    private static final int DEFAULT_PWD_WINDOW_SECONDS = 60;

    /** User-Agent 入库最大长度 */
    private static final int UA_MAX_LENGTH = 480;

    private final ShareLinkMapper shareLinkMapper;

    private final ShareAccessLogMapper shareAccessLogMapper;

    private final ShareRouteCache routeCache;

    private final ShareStorageService storageService;

    private final ShareTicketService ticketService;

    private final RedisCache redisCache;

    /**
     * 分享详情（不含文件内容）
     *
     * @param code    短码
     * @param request 请求（用于判断是否已通过密码校验）
     * @return 详情
     */
    public SharePublicDetail detail(String code, HttpServletRequest request) {
        ShareLink link = shareLinkMapper.selectShareLinkByCode(code);
        if (link == null) {
            return notFound(code);
        }
        String state = resolveState(link);
        boolean needPwd = StrUtil.isNotBlank(link.getPasswordHash());
        boolean verified = needPwd && ticketService.hasPwd(code, request);
        logAccess(link, ShareConstants.ACTION_DETAIL, ShareConstants.RESULT_SUCCESS, state, request);
        return new SharePublicDetail(
                code,
                state,
                messageOf(state),
                link.getFileName(),
                link.getFileSize(),
                link.getFileExt(),
                link.getContentType(),
                needPwd,
                verified,
                link.getCacheStatus(),
                remainingViews(link),
                link.getExpireAt(),
                ShareConstants.BURN_YES.equals(link.getBurnAfterRead()));
    }

    /**
     * 密码校验：通过后下发密码票据
     *
     * @param code     短码
     * @param password 密码
     * @param request  请求
     * @param response 响应
     */
    public void verify(String code, String password, HttpServletRequest request, HttpServletResponse response) {
        ShareLink link = shareLinkMapper.selectShareLinkByCode(code);
        if (link == null) {
            throw new ServiceException("链接不存在");
        }
        String state = resolveState(link);
        if (!ShareConstants.PUBLIC_STATE_OK.equals(state) && !ShareConstants.PUBLIC_STATE_CACHING.equals(state)) {
            logAccess(link, ShareConstants.ACTION_VERIFY, ShareConstants.RESULT_FAIL, state, request);
            throw new ServiceException(messageOf(state));
        }
        if (StrUtil.isBlank(link.getPasswordHash())) {
            // 无需密码，直接视为通过
            ticketService.issuePwd(code, request, response);
            return;
        }
        assertPasswordAttemptsAllowed(code);
        if (!SecurityUtils.matchesPassword(password, link.getPasswordHash())) {
            increasePasswordAttempts(code);
            logAccess(link, ShareConstants.ACTION_VERIFY, ShareConstants.RESULT_FAIL, "密码错误", request);
            throw new ServiceException("密码错误");
        }
        clearPasswordAttempts(code);
        ticketService.issuePwd(code, request, response);
        logAccess(link, ShareConstants.ACTION_VERIFY, ShareConstants.RESULT_SUCCESS, null, request);
    }

    /**
     * 进入就绪态：原子计数 + 焚毁判定 + 下发就绪票据
     *
     * @param code     短码
     * @param request  请求
     * @param response 响应
     * @return 进入结果
     */
    public ShareOpenResult open(String code, HttpServletRequest request, HttpServletResponse response) {
        ShareLink link = shareLinkMapper.selectShareLinkByCode(code);
        if (link == null) {
            throw new ServiceException("链接不存在");
        }
        String state = resolveState(link);
        if (!ShareConstants.PUBLIC_STATE_OK.equals(state) && !ShareConstants.PUBLIC_STATE_CACHING.equals(state)) {
            logAccess(link, ShareConstants.ACTION_OPEN, ShareConstants.RESULT_FAIL, state, request);
            throw new ServiceException(messageOf(state));
        }
        if (StrUtil.isNotBlank(link.getPasswordHash()) && !ticketService.hasPwd(code, request)) {
            throw new ServiceException("需要先校验密码");
        }
        // 原子计数：受影响行数为 1 才算一次有效访问
        int rows = shareLinkMapper.incrementViewCount(link.getLinkId());
        if (rows == 0) {
            logAccess(link, ShareConstants.ACTION_OPEN, ShareConstants.RESULT_FAIL, "次数用尽", request);
            throw new ServiceException("查看次数已用尽");
        }
        ticketService.issueView(code, request, response);
        ShareLink latest = shareLinkMapper.selectShareLinkById(link.getLinkId());
        if (latest == null) {
            // 并发删除等极端情况：本次计数已生效但记录已不在，按已用尽处理
            return new ShareOpenResult(false, 0);
        }
        boolean burned = false;
        if (ShareConstants.BURN_YES.equals(latest.getBurnAfterRead()) && isLastView(latest)) {
            shareLinkMapper.markBurned(latest.getLinkId());
            routeCache.evict(code);
            burned = true;
        }
        logAccess(link, ShareConstants.ACTION_OPEN, ShareConstants.RESULT_SUCCESS, null, request);
        return new ShareOpenResult(burned, remainingViews(latest));
    }

    /**
     * 读取文件字节流（需持有就绪票据）
     *
     * @param code     短码
     * @param action   动作（file / download），仅用于日志
     * @param request  请求
     * @return 读取结果
     */
    public StoredObject openFile(String code, String action, HttpServletRequest request) {
        if (!ticketService.hasView(code, request)) {
            throw new ServiceException("请先进入分享页面再读取文件");
        }
        ShareRoute route = routeCache.get(code);
        if (route == null) {
            ShareLink link = shareLinkMapper.selectShareLinkByCode(code);
            if (link == null) {
                throw new ServiceException("链接不存在");
            }
            route = new ShareRoute(link.getLinkId(), link.getStorageType(), link.getStorageLocation(),
                    link.getContentType(), link.getFileName(), link.getFileSize());
            if (ShareConstants.CACHE_READY.equals(link.getCacheStatus())) {
                routeCache.put(link);
            }
        }
        StoredObject object = storageService.open(route.storageType(), route.storageLocation(),
                route.contentType(), route.fileName());
        logAccess(route.linkId(), code, action, ShareConstants.RESULT_SUCCESS, null, request);
        return object;
    }

    /**
     * 计算链接当前状态
     */
    private String resolveState(ShareLink link) {
        if (ShareConstants.STATUS_BURNED.equals(link.getStatus())) {
            return ShareConstants.PUBLIC_STATE_BURNED;
        }
        if (ShareConstants.STATUS_INVALID.equals(link.getStatus())) {
            return ShareConstants.PUBLIC_STATE_INVALID;
        }
        if (link.getExpireAt() != null && link.getExpireAt().before(new Date())) {
            return ShareConstants.PUBLIC_STATE_EXPIRED;
        }
        if (remainingViews(link) != null && remainingViews(link) <= 0) {
            return ShareConstants.PUBLIC_STATE_EXHAUSTED;
        }
        if (ShareConstants.CACHE_FAILED.equals(link.getCacheStatus())) {
            return ShareConstants.PUBLIC_STATE_FAILED;
        }
        if (!ShareConstants.CACHE_READY.equals(link.getCacheStatus())) {
            return ShareConstants.PUBLIC_STATE_CACHING;
        }
        return ShareConstants.PUBLIC_STATE_OK;
    }

    /**
     * 剩余查看次数：不限时返回 null
     */
    private Integer remainingViews(ShareLink link) {
        Integer max = link.getMaxViews();
        if (max == null || max <= 0) {
            return null;
        }
        int used = link.getViewCount() == null ? 0 : link.getViewCount();
        return Math.max(0, max - used);
    }

    /**
     * 是否已是最后一次可查看
     */
    private boolean isLastView(ShareLink link) {
        Integer max = link.getMaxViews();
        if (max == null || max <= 0) {
            return true;
        }
        int used = link.getViewCount() == null ? 0 : link.getViewCount();
        return used >= max;
    }

    /**
     * 状态说明
     */
    private String messageOf(String state) {
        return switch (state) {
            case ShareConstants.PUBLIC_STATE_CACHING -> "文件准备中";
            case ShareConstants.PUBLIC_STATE_FAILED -> "文件准备失败";
            case ShareConstants.PUBLIC_STATE_EXPIRED -> "链接已过期";
            case ShareConstants.PUBLIC_STATE_INVALID -> "链接已失效";
            case ShareConstants.PUBLIC_STATE_BURNED -> "文件已焚毁";
            case ShareConstants.PUBLIC_STATE_EXHAUSTED -> "查看次数已用尽";
            case ShareConstants.PUBLIC_STATE_NOTFOUND -> "链接不存在";
            default -> null;
        };
    }

    /**
     * 构造「不存在」详情
     */
    private SharePublicDetail notFound(String code) {
        return new SharePublicDetail(code, ShareConstants.PUBLIC_STATE_NOTFOUND,
                messageOf(ShareConstants.PUBLIC_STATE_NOTFOUND), null, null, null, null,
                false, false, null, null, null, false);
    }

    /**
     * 校验密码尝试次数
     */
    private void assertPasswordAttemptsAllowed(String code) {
        int maxAttempts = ConfigUtils.getInt(ConfigConstants.SHARE_PASSWORD_MAX_ATTEMPTS, DEFAULT_PWD_MAX_ATTEMPTS);
        Integer attempts = redisCache.getCacheObject(attemptKey(code));
        if (attempts != null && attempts >= maxAttempts) {
            throw new ServiceException("密码尝试次数过多，请稍后再试");
        }
    }

    /**
     * 累加密码尝试次数
     */
    private void increasePasswordAttempts(String code) {
        int windowSeconds = ConfigUtils.getInt(ConfigConstants.SHARE_PASSWORD_WINDOW_SECONDS, DEFAULT_PWD_WINDOW_SECONDS);
        String key = attemptKey(code);
        Integer attempts = redisCache.getCacheObject(key);
        int next = (attempts == null ? 0 : attempts) + 1;
        redisCache.setCacheObject(key, next, windowSeconds, TimeUnit.SECONDS);
    }

    /**
     * 清空密码尝试次数
     */
    private void clearPasswordAttempts(String code) {
        redisCache.deleteObject(attemptKey(code));
    }

    /**
     * 密码尝试计数键（按短码 + IP）
     */
    private String attemptKey(String code) {
        return ShareConstants.PWD_ATTEMPT_KEY_PREFIX + code + ":" + IpUtils.getIpAddr();
    }

    /**
     * 写访问日志（失败只记本地日志，不影响主流程）
     */
    private void logAccess(ShareLink link, String action, String result, String message, HttpServletRequest request) {
        logAccess(link.getLinkId(), link.getCode(), action, result, message, request);
    }

    /**
     * 写访问日志（失败只记本地日志，不影响主流程）
     */
    private void logAccess(Long linkId, String code, String action, String result, String message,
                           HttpServletRequest request) {
        try {
            ShareAccessLog accessLog = new ShareAccessLog();
            accessLog.setLinkId(linkId);
            accessLog.setCode(code);
            accessLog.setIp(IpUtils.getIpAddr());
            accessLog.setUserAgent(userAgent(request));
            accessLog.setAction(action);
            accessLog.setResult(result);
            accessLog.setMessage(message);
            shareAccessLogMapper.insertShareAccessLog(accessLog);
        } catch (Exception e) {
            log.warn("写分享访问日志失败：code={}, action={}", code, action, e);
        }
    }

    /**
     * 取 User-Agent 并截断
     */
    private String userAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return "";
        }
        return ua.length() > UA_MAX_LENGTH ? ua.substring(0, UA_MAX_LENGTH) : ua;
    }
}
