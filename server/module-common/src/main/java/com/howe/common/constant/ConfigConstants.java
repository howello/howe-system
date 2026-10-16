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

    /** 朋友圈 RSS 同步任务开关（module-quartz 移除后替代原 sys_job 启停） */
    public static final String BLOG_FEED_SYNC_ENABLED = "blog.feed.syncEnabled";

    /** waline 友链同步任务开关（module-quartz 移除后替代原 sys_job 启停） */
    public static final String BLOG_LINK_SYNC_ENABLED = "blog.link.syncEnabled";

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

    /** 首页模块可用性探测结果缓存 TTL（秒），参数页修改即时生效 */
    public static final String HOME_MODULES_CACHE_TTL = "sys.home.moduleCacheTtl";

    /** 首页统计类卡片结果缓存 TTL（秒），覆盖点餐/分享/AI 等模块卡片 */
    public static final String HOME_CARD_CACHE_TTL = "sys.home.cardCacheTtl";

    /** waline 评论列表端点（默认 https://waline.wyantao.com/api/comment） */
    public static final String BLOG_WALINE_URL = "blog.waline.url";

    /** waline 每页条数（默认 10） */
    public static final String BLOG_WALINE_PAGE_SIZE = "blog.waline.pageSize";

    /** waline 请求超时（毫秒，默认 30000） */
    public static final String BLOG_WALINE_TIMEOUT = "blog.waline.timeout";

    /** AI 模块总开关，关闭时所有 AI 调用直接抛 AI_DISABLED */
    public static final String AI_ENABLED = "ai.enabled";

    /** 全局默认对话模型 ID */
    public static final String AI_DEFAULT_CHAT_MODEL = "ai.default.chat.model";

    /** 全局默认视觉模型 ID */
    public static final String AI_DEFAULT_VISION_MODEL = "ai.default.vision.model";

    /** 全局默认文生图模型 ID */
    public static final String AI_DEFAULT_IMAGE_MODEL = "ai.default.image.model";

    /** 全局默认向量模型 ID */
    public static final String AI_DEFAULT_EMBEDDING_MODEL = "ai.default.embedding.model";

    /** 是否启用降级链 */
    public static final String AI_FALLBACK_ENABLED = "ai.fallback.enabled";

    /** 同模型瞬时错误重试次数 */
    public static final String AI_RETRY_MAX = "ai.retry.max";

    /** 重试退避基数（毫秒） */
    public static final String AI_RETRY_BACKOFF_MS = "ai.retry.backoffMs";

    /** 对话超时（毫秒） */
    public static final String AI_TIMEOUT_CHAT = "ai.timeout.chat";

    /** 文生图超时（毫秒） */
    public static final String AI_TIMEOUT_IMAGE = "ai.timeout.image";

    /** 单次最多生成张数 */
    public static final String AI_IMAGE_MAX_COUNT = "ai.image.maxCount";

    /**
     * 风格后缀模板的键名前缀
     *
     * <p>完整键名形如 {@code ai.image.styleSuffix.recipe.photo}，
     * 由本前缀加业务方传入的 style key 拼成，因此只登记前缀。</p>
     */
    public static final String AI_IMAGE_STYLE_SUFFIX_PREFIX = "ai.image.styleSuffix.";

    /** 调用记录保留天数 */
    public static final String AI_LOG_RETENTION_DAYS = "ai.log.retentionDays";

    /** 生成图转存：请求内重试次数 */
    public static final String AI_IMAGE_TRANSFER_RETRY_MAX = "ai.image.transfer.retryMax";

    /** 生成图转存：请求内重试退避基数（毫秒） */
    public static final String AI_IMAGE_TRANSFER_RETRY_BACKOFF_MS = "ai.image.transfer.retryBackoffMs";

    /** 生成图转存补偿：是否启用定时重传 */
    public static final String AI_IMAGE_TRANSFER_COMPENSATE_ENABLED = "ai.image.transfer.compensateEnabled";

    /** 生成图转存补偿：单条任务最大重传次数 */
    public static final String AI_IMAGE_TRANSFER_COMPENSATE_MAX_ATTEMPT = "ai.image.transfer.compensateMaxAttempt";

    /** 生成图转存补偿：任务保留时长（分钟），超时丢弃 */
    public static final String AI_IMAGE_TRANSFER_EXPIRE_MINUTES = "ai.image.transfer.expireMinutes";

    /** 菜品一键 AI 文本补齐的 system 提示词 */
    public static final String AI_PROMPT_MEAL_DISH_SYSTEM = "ai.prompt.meal.dish.system";

    /** 新菜提案 AI 自动审核在 system 提示词之后追加的判定说明 */
    public static final String AI_PROMPT_MEAL_DISH_AUDIT_SUFFIX = "ai.prompt.meal.dish.auditSuffix";

    /**
     * 点餐 App 版本发布令牌
     *
     * <p>给打包脚本（`meal-ui` 的 `publish:wgt`）用的静态令牌：请求头 {@code X-Publish-Token}
     * 与之匹配即可调用发布接口（上传 wgt 到 R2 并新建版本记录），不依赖登录、不参与验证码与人机校验。
     * 为空表示未配置，发布接口一律拒绝。</p>
     */
    public static final String MEAL_APP_PUBLISH_TOKEN = "meal.app.publishToken";

    /** 文件分享：本地缓存大小上限（字节，超过落 R2） */
    public static final String SHARE_CACHE_LOCAL_MAX_SIZE = "share.cache.localMaxSize";

    /** 文件分享：文档白名单（这些扩展名且不超上限的文件落本地），逗号分隔 */
    public static final String SHARE_CACHE_DOC_EXTS = "share.cache.docExts";

    /** 文件分享：缓存失败自动重试开关 */
    public static final String SHARE_CACHE_RETRY_ENABLED = "share.cache.retryEnabled";

    /** 文件分享：缓存最大重试次数 */
    public static final String SHARE_CACHE_MAX_RETRY = "share.cache.maxRetry";

    /** 文件分享：Redis 路由缓存开关 */
    public static final String SHARE_ROUTE_CACHE_ENABLED = "share.route.cacheEnabled";

    /** 文件分享：无过期时间时的路由缓存 TTL（秒） */
    public static final String SHARE_ROUTE_TTL = "share.route.ttl";

    /** 文件分享：密码尝试次数上限 */
    public static final String SHARE_PASSWORD_MAX_ATTEMPTS = "share.password.maxAttempts";

    /** 文件分享：密码尝试统计窗口（秒） */
    public static final String SHARE_PASSWORD_WINDOW_SECONDS = "share.password.windowSeconds";

    /** 文件分享：会话票据有效期（秒） */
    public static final String SHARE_TICKET_TTL = "share.ticket.ttl";

    /** 文件分享：单文件下载上限（字节） */
    public static final String SHARE_DOWNLOAD_MAX_SIZE = "share.download.maxSize";

    /** 文件分享：访问日志保留天数 */
    public static final String SHARE_ACCESS_LOG_RETAIN_DAYS = "share.access.log.retainDays";

    /** 文件分享：短链对外基址（末尾带 /），用于拼列表展示的短链 */
    public static final String SHARE_BASE_URL = "share.baseUrl";
}
