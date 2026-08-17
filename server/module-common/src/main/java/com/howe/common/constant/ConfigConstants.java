package com.howe.common.constant;

/**
 * 参数配置键名
 *
 * <p>
 * 全部维护在「系统管理 &gt; 参数设置」里，改完即时生效。放在常量类里是为了让
 * 后端取值与初始化 SQL 的键名不会写岔。
 * </p>
 *
 * @author howe
 */
public class ConfigConstants
{
    /** 存储类型：r2 对象存储 / local 本地磁盘 */
    public static final String STORAGE_TYPE = "sys.storage.type";

    /** R2 的 S3 兼容端点 */
    public static final String STORAGE_R2_ENDPOINT = "sys.storage.r2.endpoint";

    /** R2 访问密钥 ID */
    public static final String STORAGE_R2_ACCESS_KEY = "sys.storage.r2.accessKeyId";

    /** R2 访问密钥 */
    public static final String STORAGE_R2_SECRET_KEY = "sys.storage.r2.secretAccessKey";

    /** R2 存储桶 */
    public static final String STORAGE_R2_BUCKET = "sys.storage.r2.bucket";

    /** R2 公开访问域名 */
    public static final String STORAGE_R2_PUBLIC_URL = "sys.storage.r2.publicUrl";

    /** R2 对象键前缀 */
    public static final String STORAGE_R2_KEY_PREFIX = "sys.storage.r2.keyPrefix";

    /** GitHub API 基址 */
    public static final String BLOG_GITHUB_API_BASE = "blog.github.apiBase";

    /** GitHub 仓库拥有者 */
    public static final String BLOG_GITHUB_OWNER = "blog.github.owner";

    /** GitHub 仓库名 */
    public static final String BLOG_GITHUB_REPO = "blog.github.repo";

    /** GitHub 目标分支 */
    public static final String BLOG_GITHUB_BRANCH = "blog.github.branch";

    /** GitHub 访问令牌 */
    public static final String BLOG_GITHUB_TOKEN = "blog.github.token";

    /** 文章目录，相对仓库根 */
    public static final String BLOG_CONTENT_DIR = "blog.github.contentDir";

    /** 提交作者名 */
    public static final String BLOG_COMMITTER_NAME = "blog.github.committerName";

    /** 提交作者邮箱 */
    public static final String BLOG_COMMITTER_EMAIL = "blog.github.committerEmail";

    /** Webhook 密钥 */
    public static final String BLOG_WEBHOOK_SECRET = "blog.github.webhookSecret";

    /** GitHub 请求超时（毫秒） */
    public static final String BLOG_GITHUB_TIMEOUT = "blog.github.timeout";

    /** 博客开放接口开关（外部 agent 投稿用） */
    public static final String BLOG_OPEN_ENABLED = "blog.open.enabled";

    /** 博客开放接口令牌，空值等于关闭 */
    public static final String BLOG_OPEN_TOKEN = "blog.open.token";

    /** RSS 抓取超时（毫秒） */
    public static final String BLOG_FEED_TIMEOUT = "blog.feed.timeout";

    /** RSS 响应体大小上限（字节），防超大 RSS 撑爆内存 */
    public static final String BLOG_FEED_MAX_SIZE = "blog.feed.maxSize";

    /** RSS 摘要剥离 HTML 后的截断字数 */
    public static final String BLOG_FEED_SUMMARY_LENGTH = "blog.feed.summaryLength";

    /** RSS 请求 User-Agent，部分站点拒绝默认 UA */
    public static final String BLOG_FEED_USER_AGENT = "blog.feed.userAgent";

    /** 博客站点公共 URL 基址，AI 博客 Tool 据此拼文章公开链接（如 https://www.wyantao.com） */
    public static final String BLOG_SITE_URL = "blog.site.url";

    /** 验证码开关 */
    public static final String CAPTCHA_ENABLED = "sys.account.captchaEnabled";

    /**
     * 验证码类型
     *
     * <p>
     * 取值见 {@code CaptchaType}：char/math/line/circle/shear/gif，另支持 random 每次随机。
     * 留空时回落到 yml 的 {@code howe.captchaType}。
     * </p>
     */
    public static final String CAPTCHA_TYPE = "sys.account.captchaType";

    /** Cloudflare Turnstile 真人校验开关 */
    public static final String TURNSTILE_ENABLED = "sys.turnstile.enabled";

    /** Turnstile 站点密钥，前端渲染组件用，可公开 */
    public static final String TURNSTILE_SITE_KEY = "sys.turnstile.siteKey";

    /** Turnstile 服务端密钥，只在后端校验时使用，不得下发前端 */
    public static final String TURNSTILE_SECRET_KEY = "sys.turnstile.secretKey";

    /** Turnstile 校验接口地址 */
    public static final String TURNSTILE_VERIFY_URL = "sys.turnstile.verifyUrl";

    /** Turnstile 校验超时（毫秒） */
    public static final String TURNSTILE_TIMEOUT = "sys.turnstile.timeout";

    /** 首页博客统计聚合结果缓存 TTL（秒），参数页修改即时生效 */
    public static final String HOME_STATS_CACHE_TTL = "sys.home.statsCacheTtl";
}
