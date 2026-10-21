package com.howe.common.constant;

/**
 * 缓存的key 常量
 *
 * @author howe
 */
public class CacheConstants
{
    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /**
     * 家庭邀请码 redis key 前缀
     *
     * <p>
     * 键名形如 {@code family_code:{邀请码}}，值是部门ID。邀请码是短期凭证，
     * 放 Redis 天然带 TTL，不入库——过期即失效，也不用担心历史码残留在表里。
     * </p>
     */
    public static final String FAMILY_CODE_KEY = "family_code:";

    /**
     * 首页统计缓存 key（完整键名，无冒号后缀）
     */
    public static final String HOME_STATS_KEY = "home:stats:summary";

    /**
     * 首页模块可用性探测缓存 key（完整键名，无冒号后缀）
     */
    public static final String HOME_MODULES_KEY = "home:modules:availability";

    /** 首页点餐统计缓存 key 前缀（实际键按数据范围追加后缀） */
    public static final String HOME_MEAL_STATS_KEY = "home:meal:stats";

    /** 首页点餐菜品概况缓存 key 前缀 */
    public static final String HOME_MEAL_DISH_KEY = "home:meal:dish";

    /** 首页点餐 App 版本概况缓存 key（平台级，不分家庭） */
    public static final String HOME_MEAL_VERSION_KEY = "home:meal:version";

    /** 首页点餐热门菜品缓存 key 前缀 */
    public static final String HOME_MEAL_HOT_DISH_KEY = "home:meal:hotdish";

    /** 首页分享统计缓存 key（平台级，不分家庭） */
    public static final String HOME_SHARE_STATS_KEY = "home:share:stats";

    /** 首页分享缓存健康缓存 key */
    public static final String HOME_SHARE_CACHE_KEY = "home:share:cache";

    /** 首页 AI 用量统计缓存 key */
    public static final String HOME_AI_STATS_KEY = "home:ai:stats";

    /** 首页 AI 渠道健康缓存 key */
    public static final String HOME_AI_CHANNEL_KEY = "home:ai:channel";

    /** 首页 AI 图片资产概况缓存 key */
    public static final String HOME_AI_IMAGE_KEY = "home:ai:image";

    /** 首页博客友链与订阅同步缓存 key */
    public static final String HOME_BLOG_LINK_KEY = "home:blog:link";
}
