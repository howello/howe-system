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
}
