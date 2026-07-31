package com.howe.framework.captcha;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.user.TurnstileException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.ip.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Cloudflare Turnstile 真人校验
 *
 * <p>
 * 图形验证码只能挡住最粗糙的脚本，Turnstile 补的是「这次请求来自真人浏览器」这一层。
 * 两者是叠加关系而不是替代：验证码开关与 Turnstile 开关各自独立，可以只开一个。
 * </p>
 *
 * <p>
 * 密钥放在「系统管理 &gt; 参数设置」里，改完即时生效。
 * <b>开关打开但密钥没配时一律拒绝</b>——静默放行会让人误以为防护还在。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Component
public class TurnstileService
{
    /** Cloudflare 官方校验端点 */
    private static final String DEFAULT_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    /** 默认超时（毫秒），校验卡住不能拖垮登录接口 */
    private static final int DEFAULT_TIMEOUT = 5000;

    /**
     * 是否启用真人校验
     */
    public boolean isEnabled()
    {
        return ConfigUtils.getBoolean(ConfigConstants.TURNSTILE_ENABLED, false);
    }

    /**
     * 站点密钥
     *
     * <p>
     * 这个值本来就要嵌进前端页面，随 {@code /captchaImage} 一起下发是安全的。
     * </p>
     */
    public String getSiteKey()
    {
        return ConfigUtils.getString(ConfigConstants.TURNSTILE_SITE_KEY);
    }

    /**
     * 校验前端提交的 Turnstile 令牌
     *
     * <p>
     * 未启用时直接放行。启用了就必须拿到令牌并通过 Cloudflare 校验，
     * 网络异常也算失败——宁可让用户重试，也不能因为一次超时就把门敞开。
     * </p>
     *
     * @param token 前端 Turnstile 组件产出的令牌
     * @throws TurnstileException 校验未通过
     */
    public void validate(String token)
    {
        if (!isEnabled())
        {
            return;
        }
        String secretKey = ConfigUtils.getString(ConfigConstants.TURNSTILE_SECRET_KEY);
        if (StringUtils.isEmpty(secretKey))
        {
            log.error("已开启 Turnstile 但未配置服务端密钥（{}），拒绝本次请求",
                    ConfigConstants.TURNSTILE_SECRET_KEY);
            throw new TurnstileException("user.turnstile.unconfigured");
        }
        if (StringUtils.isEmpty(token))
        {
            throw new TurnstileException("user.turnstile.required");
        }

        Map<String, Object> form = new HashMap<>(3);
        form.put("secret", secretKey);
        form.put("response", token);
        // 带上客户端 IP，Cloudflare 侧可以做额外的风控判断
        String clientIp = IpUtils.getIpAddr();
        if (StringUtils.isNotEmpty(clientIp))
        {
            form.put("remoteip", clientIp);
        }

        String verifyUrl = ConfigUtils.getString(ConfigConstants.TURNSTILE_VERIFY_URL, DEFAULT_VERIFY_URL);
        int timeout = ConfigUtils.getInt(ConfigConstants.TURNSTILE_TIMEOUT, DEFAULT_TIMEOUT);
        String body;
        try (HttpResponse response = HttpRequest.post(verifyUrl).form(form).timeout(timeout).execute())
        {
            body = response.body();
            if (!response.isOk())
            {
                log.error("Turnstile 校验接口返回 {}：{}", response.getStatus(), body);
                throw new TurnstileException();
            }
        }
        catch (TurnstileException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("Turnstile 校验请求失败", e);
            throw new TurnstileException();
        }

        JSONObject result = JSONObject.parseObject(body);
        if (result == null || !Boolean.TRUE.equals(result.getBoolean("success")))
        {
            log.warn("Turnstile 校验未通过：{}", errorCodes(result));
            throw new TurnstileException();
        }
    }

    /**
     * 取 Cloudflare 返回的错误码，仅用于日志
     */
    private String errorCodes(JSONObject result)
    {
        if (result == null)
        {
            return "响应为空";
        }
        JSONArray codes = result.getJSONArray("error-codes");
        return codes == null ? "无错误码" : codes.toJSONString();
    }
}
