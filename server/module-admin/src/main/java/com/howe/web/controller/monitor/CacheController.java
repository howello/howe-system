package com.howe.web.controller.monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.howe.common.constant.CacheConstants;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.utils.StringUtils;
import com.howe.system.domain.SysCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 缓存监控
 * 
 * @author howe
 */
@Tag(name = "缓存监控", description = "Redis 缓存信息查询与缓存清理")
@RestController
@RequestMapping("/monitor/cache")
public class CacheController
{
    @Autowired
    @Qualifier("stringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    private final static List<SysCache> caches = new ArrayList<SysCache>();
    {
        caches.add(new SysCache(CacheConstants.LOGIN_TOKEN_KEY, "用户信息"));
        caches.add(new SysCache(CacheConstants.SYS_CONFIG_KEY, "配置信息"));
        caches.add(new SysCache(CacheConstants.SYS_DICT_KEY, "数据字典"));
        caches.add(new SysCache(CacheConstants.CAPTCHA_CODE_KEY, "验证码"));
        caches.add(new SysCache(CacheConstants.REPEAT_SUBMIT_KEY, "防重提交"));
        caches.add(new SysCache(CacheConstants.RATE_LIMIT_KEY, "限流处理"));
        caches.add(new SysCache(CacheConstants.PWD_ERR_CNT_KEY, "密码错误次数"));
    }

    @SuppressWarnings("deprecation")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存监控信息", description = "返回 Redis 服务信息、键总数以及各命令调用次数统计")
    @GetMapping()
    public AjaxResult getInfo() throws Exception
    {
        Properties info = (Properties) redisTemplate.execute((RedisCallback<Object>) connection -> connection.info());
        Properties commandStats = (Properties) redisTemplate.execute((RedisCallback<Object>) connection -> connection.info("commandstats"));
        Object dbSize = redisTemplate.execute((RedisCallback<Object>) connection -> connection.dbSize());

        Map<String, Object> result = new HashMap<>(3);
        result.put("info", info);
        result.put("dbSize", dbSize);

        List<Map<String, String>> pieList = new ArrayList<>();
        commandStats.stringPropertyNames().forEach(key -> {
            Map<String, String> data = new HashMap<>(2);
            String property = commandStats.getProperty(key);
            data.put("name", StringUtils.removeStart(key, "cmdstat_"));
            data.put("value", StringUtils.substringBetween(property, "calls=", ",usec"));
            pieList.add(data);
        });
        result.put("commandStats", pieList);
        return AjaxResult.success(result);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存名称列表", description = "返回系统预置的缓存分类名称")
    @GetMapping("/getNames")
    public AjaxResult cache()
    {
        return AjaxResult.success(caches);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存键名列表", description = "按缓存名称前缀模糊匹配所有键名")
    @GetMapping("/getKeys/{cacheName}")
    public AjaxResult getCacheKeys(@Parameter(description = "缓存名称") @PathVariable String cacheName)
    {
        Set<String> cacheKeys = redisTemplate.keys(cacheName + "*");
        return AjaxResult.success(new TreeSet<>(cacheKeys));
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "获取缓存内容", description = "根据缓存名称与键名查询缓存值；登录令牌、验证码等敏感缓存只返回脱敏摘要")
    @GetMapping("/getValue/{cacheName}/{cacheKey}")
    public AjaxResult getCacheValue(@Parameter(description = "缓存名称") @PathVariable String cacheName, @Parameter(description = "缓存键名") @PathVariable String cacheKey)
    {
        String cacheValue = redisTemplate.opsForValue().get(cacheKey);
        SysCache sysCache = new SysCache(cacheName, cacheKey, maskSensitiveValue(cacheName, cacheValue));
        return AjaxResult.success(sysCache);
    }

    /**
     * 对敏感缓存内容脱敏
     *
     * <p>
     * {@code login_tokens:*} 存的是完整 {@code LoginUser}（含 token 与权限集合），
     * {@code captcha_codes:*} 是未过期的验证码答案，这两类一旦被原样读出即可直接复用他人会话。
     * 监控页只需要确认「键存在、有内容」，因此这里只返回长度与首尾片段，不给出可用凭据。
     * </p>
     *
     * @param cacheName 缓存名称（键名前缀）
     * @param cacheValue 缓存原始值
     * @return 脱敏后的值；非敏感缓存原样返回
     */
    private String maskSensitiveValue(String cacheName, String cacheValue)
    {
        if (StringUtils.isEmpty(cacheValue) || !isSensitiveCache(cacheName))
        {
            return cacheValue;
        }
        int length = cacheValue.length();
        if (length <= 8)
        {
            return "******（已脱敏，长度 " + length + "）";
        }
        return cacheValue.substring(0, 4) + "******" + cacheValue.substring(length - 4)
                + "（已脱敏，长度 " + length + "）";
    }

    /**
     * 判断缓存名称是否属于敏感类别
     *
     * @param cacheName 缓存名称（键名前缀）
     * @return true 表示该类别的内容不得原样下发
     */
    private boolean isSensitiveCache(String cacheName)
    {
        if (StringUtils.isEmpty(cacheName))
        {
            return false;
        }
        // 传入的 cacheName 可能带或不带结尾冒号，统一补齐后再比较
        String prefix = StringUtils.endsWith(cacheName, ":") ? cacheName : cacheName + ":";
        return StringUtils.equals(prefix, CacheConstants.LOGIN_TOKEN_KEY)
                || StringUtils.equals(prefix, CacheConstants.CAPTCHA_CODE_KEY)
                || StringUtils.equals(prefix, CacheConstants.PWD_ERR_CNT_KEY)
                // 参数缓存里同样有存储密钥、博客令牌等凭据，明文下发等于再开一个出口
                || StringUtils.equals(prefix, CacheConstants.SYS_CONFIG_KEY);
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "清理指定名称缓存", description = "删除该缓存名称前缀下的全部键")
    @DeleteMapping("/clearCacheName/{cacheName}")
    public AjaxResult clearCacheName(@Parameter(description = "缓存名称") @PathVariable String cacheName)
    {
        Collection<String> cacheKeys = redisTemplate.keys(cacheName + "*");
        redisTemplate.delete(cacheKeys);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "清理指定键名缓存", description = "删除单个缓存键")
    @DeleteMapping("/clearCacheKey/{cacheKey}")
    public AjaxResult clearCacheKey(@Parameter(description = "缓存键名") @PathVariable String cacheKey)
    {
        redisTemplate.delete(cacheKey);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @Operation(summary = "清理全部缓存", description = "删除当前 Redis 库中的所有键")
    @DeleteMapping("/clearCacheAll")
    public AjaxResult clearCacheAll()
    {
        Collection<String> cacheKeys = redisTemplate.keys("*");
        redisTemplate.delete(cacheKeys);
        return AjaxResult.success();
    }
}
