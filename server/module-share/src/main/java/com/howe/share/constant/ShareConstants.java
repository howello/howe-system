package com.howe.share.constant;

/**
 * 文件分享模块常量
 *
 * @author howe
 */
public final class ShareConstants {

    private ShareConstants() {
    }

    /** 存储位置：本地磁盘 */
    public static final String STORAGE_LOCAL = "0";

    /** 存储位置：R2 对象存储 */
    public static final String STORAGE_R2 = "1";

    /** 缓存状态：待缓存 */
    public static final String CACHE_PENDING = "0";

    /** 缓存状态：缓存中 */
    public static final String CACHE_RUNNING = "1";

    /** 缓存状态：就绪 */
    public static final String CACHE_READY = "2";

    /** 缓存状态：失败 */
    public static final String CACHE_FAILED = "3";

    /** 链接状态：正常 */
    public static final String STATUS_NORMAL = "0";

    /** 链接状态：已失效 */
    public static final String STATUS_INVALID = "1";

    /** 链接状态：已焚毁 */
    public static final String STATUS_BURNED = "2";

    /** 阅后即焚：是 */
    public static final String BURN_YES = "1";

    /** 阅后即焚：否 */
    public static final String BURN_NO = "0";

    /** 公开端状态：就绪 */
    public static final String PUBLIC_STATE_OK = "ok";

    /** 公开端状态：缓存中 */
    public static final String PUBLIC_STATE_CACHING = "caching";

    /** 公开端状态：缓存失败 */
    public static final String PUBLIC_STATE_FAILED = "failed";

    /** 公开端状态：已过期 */
    public static final String PUBLIC_STATE_EXPIRED = "expired";

    /** 公开端状态：已失效 */
    public static final String PUBLIC_STATE_INVALID = "invalid";

    /** 公开端状态：已焚毁 */
    public static final String PUBLIC_STATE_BURNED = "burned";

    /** 公开端状态：次数用尽 */
    public static final String PUBLIC_STATE_EXHAUSTED = "exhausted";

    /** 公开端状态：不存在 */
    public static final String PUBLIC_STATE_NOTFOUND = "notfound";

    /** 访问动作：详情 */
    public static final String ACTION_DETAIL = "detail";

    /** 访问动作：密码校验 */
    public static final String ACTION_VERIFY = "verify";

    /** 访问动作：进入就绪态 */
    public static final String ACTION_OPEN = "open";

    /** 访问动作：读取字节流 */
    public static final String ACTION_FILE = "file";

    /** 访问动作：下载 */
    public static final String ACTION_DOWNLOAD = "download";

    /** 访问结果：成功 */
    public static final String RESULT_SUCCESS = "success";

    /** 访问结果：失败 */
    public static final String RESULT_FAIL = "fail";

    /** Redis 路由缓存键前缀 */
    public static final String ROUTE_KEY_PREFIX = "share_route:";

    /** 密码校验票据键前缀 */
    public static final String TICKET_KEY_PWD = "share_pwd_ticket:";

    /** 就绪态会话票据键前缀 */
    public static final String TICKET_KEY_VIEW = "share_view_ticket:";

    /** 密码尝试计数键前缀 */
    public static final String PWD_ATTEMPT_KEY_PREFIX = "share_pwd_attempt:";

    /** 密码校验票据 Cookie 名称 */
    public static final String TICKET_COOKIE_PWD = "share_pwd";

    /** 就绪态会话票据 Cookie 名称 */
    public static final String TICKET_COOKIE_VIEW = "share_view";
}
