package com.howe.blog.controller;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.howe.blog.domain.vo.BlogSyncResult;
import com.howe.blog.service.IBlogArticleService;
import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * GitHub Webhook 控制层
 *
 * <p>
 * 接收仓库的 push 事件并增量更新文章索引，这样直接在 GitHub 上改文章、或用别的工具提交，
 * 后台列表也能跟着变。接口是匿名的，靠 X-Hub-Signature-256 的 HMAC 签名校验来源。
 * </p>
 *
 * @author howe
 */
@RestController
@RequestMapping("/blog/webhook")
public class BlogWebhookController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(BlogWebhookController.class);

    private static final String SIGNATURE_PREFIX = "sha256=";

    @Autowired
    private IBlogArticleService blogArticleService;

    /**
     * 接收 GitHub push 事件
     *
     * @param payload 请求体原文，签名针对原文计算，不能用反序列化后的对象重新拼
     * @param signature X-Hub-Signature-256 头
     * @param event X-GitHub-Event 头
     */
    @Anonymous
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    @PostMapping("/github")
    public AjaxResult github(@RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event)
    {
        String secret = ConfigUtils.getString(ConfigConstants.BLOG_WEBHOOK_SECRET);
        if (StringUtils.isEmpty(secret))
        {
            // 没配密钥就等于没开启，直接拒绝，避免裸奔的同步入口
            log.warn("收到 webhook 请求但未配置密钥，已拒绝");
            return AjaxResult.error("Webhook 未启用");
        }
        if (!verifySignature(payload, signature, secret))
        {
            log.warn("webhook 签名校验失败");
            return AjaxResult.error("签名校验失败");
        }
        if (StringUtils.isNotEmpty(event) && !"push".equals(event))
        {
            // ping 等其它事件直接确认，避免 GitHub 侧显示投递失败
            return AjaxResult.success("已忽略事件：" + event);
        }

        BlogSyncResult result = blogArticleService.handlePushEvent(payload);
        log.info("webhook 同步完成，新增 {}，更新 {}，删除 {}", result.getAdded(), result.getUpdated(), result.getRemoved());
        return AjaxResult.success("同步完成", result);
    }

    /**
     * 校验 HMAC-SHA256 签名
     */
    private boolean verifySignature(String payload, String signature, String secret)
    {
        if (StringUtils.isEmpty(signature) || !signature.startsWith(SIGNATURE_PREFIX))
        {
            return false;
        }
        String expected = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8))
                .digestHex(payload);
        // 定长比较，避免因提前返回泄漏签名信息
        return constantTimeEquals(expected, signature.substring(SIGNATURE_PREFIX.length()));
    }

    private static boolean constantTimeEquals(String a, String b)
    {
        if (a == null || b == null || a.length() != b.length())
        {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++)
        {
            diff |= Character.toLowerCase(a.charAt(i)) ^ Character.toLowerCase(b.charAt(i));
        }
        return diff == 0;
    }
}
