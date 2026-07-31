package com.howe.web.controller.common;

import com.howe.common.core.domain.AjaxResult;
import com.howe.framework.captcha.CaptchaService;
import com.howe.framework.captcha.CaptchaVo;
import com.howe.framework.captcha.TurnstileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码操作处理
 *
 * <p>
 * 一次请求把登录页需要的两层校验信息都取回去：图形验证码（图片 + 标识）
 * 与 Cloudflare Turnstile 的开关和站点密钥。两者互相独立，可以只开一个。
 * </p>
 *
 * @author howe
 */
@Tag(name = "验证码", description = "登录页图形验证码与人机校验")
@RestController
public class CaptchaController
{
    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private TurnstileService turnstileService;

    /**
     * 生成验证码
     */
    @Operation(summary = "获取验证码", description = "返回图形验证码图片与人机校验站点密钥，匿名可访问")
    @GetMapping("/captchaImage")
    public AjaxResult getCode()
    {
        AjaxResult ajax = AjaxResult.success();
        // Turnstile 的开关与站点密钥跟验证码开关无关，任何情况下都要下发
        boolean turnstileEnabled = turnstileService.isEnabled();
        ajax.put("turnstileEnabled", turnstileEnabled);
        if (turnstileEnabled)
        {
            ajax.put("turnstileSiteKey", turnstileService.getSiteKey());
        }

        boolean captchaEnabled = captchaService.isEnabled();
        ajax.put("captchaEnabled", captchaEnabled);
        if (!captchaEnabled)
        {
            return ajax;
        }

        CaptchaVo captcha = captchaService.create();
        ajax.put("uuid", captcha.uuid());
        ajax.put("img", captcha.img());
        // 图片格式随类型变（GIF 验证码不是 png），前端据此拼 data URI 前缀
        ajax.put("imgType", captcha.imgType());
        ajax.put("captchaType", captcha.captchaType());
        return ajax;
    }
}
