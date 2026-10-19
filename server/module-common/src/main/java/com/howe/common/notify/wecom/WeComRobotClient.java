package com.howe.common.notify.wecom;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信群机器人客户端
 *
 * <p>面向全项目复用的公共能力：覆盖官方全部消息类型（text / markdown / markdown_v2 /
 * image / news / file / voice / template_card）与 file / voice 的文件上传。
 * 统一处理固定官方地址、超时、按 webhook key 限流、响应解析与日志脱敏。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>webhook key 是凭证，日志只输出末尾 4 位，Redis 限流键使用 key 的 SHA-256 摘要；</li>
 *   <li>参数校验在发送前完成，校验失败直接返回失败结果，不发起 HTTP 请求；</li>
 *   <li>网络异常、超时、非 0 errcode 只返回失败结果并记录日志，<b>不抛异常、不重试</b>；</li>
 *   <li>所有类型共享每分钟 18 条的限流（官方上限 20 条），超限丢弃并记录日志。</li>
 * </ul>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComRobotClient {

    /** 发送接口地址 */
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send";

    /** 文件上传接口地址 */
    private static final String UPLOAD_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/upload_media";

    /** HTTP 超时（毫秒） */
    private static final int TIMEOUT_MS = 5000;

    /** 每个 webhook 每分钟最多发送条数（官方 20 条，留余量） */
    private static final int RATE_LIMIT_PER_MINUTE = 18;

    /** 限流 Redis 键前缀 */
    private static final String RATE_KEY_PREFIX = "wecom:rate:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送任意类型的消息
     *
     * @param webhookKey webhook key
     * @param message    消息
     * @return 发送结果；参数错误、限流或远程失败时 success=false
     */
    public WeComRobotResponse send(String webhookKey, WeComMessage message) {
        if (message == null) {
            return WeComRobotResponse.fail(-1, "消息不能为空");
        }
        String messageType = message.msgType();
        if (WeComTextUtils.isBlank(webhookKey)) {
            return WeComRobotResponse.fail(-1, "Webhook Key 未配置");
        }
        String validation = message.validate();
        if (validation != null) {
            return WeComRobotResponse.fail(-1, validation);
        }
        String key = webhookKey.trim();
        if (!tryAcquireRateLimit(key)) {
            log.warn("企业微信消息触发限流，已丢弃：key={}, type={}", WeComTextUtils.maskKey(key), messageType);
            return WeComRobotResponse.fail(-1, "发送频率超过限制，消息已丢弃");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("msgtype", messageType);
            body.put(messageType, message.payload());
            String url = UrlBuilder.of(SEND_URL).addQuery("key", key).build();
            try (HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(body.toJSONString())
                    .timeout(TIMEOUT_MS)
                    .execute()) {
                return parse(response.body());
            }
        } catch (Exception e) {
            // 不打印异常对象：hutool 的异常消息可能带完整 URL（含 key）
            log.warn("企业微信消息发送失败：key={}, type={}, error={}",
                    WeComTextUtils.maskKey(key), messageType, e.getClass().getSimpleName());
            return WeComRobotResponse.fail(-1, "企业微信消息发送失败");
        }
    }

    /**
     * 发送 text 消息
     *
     * @param webhookKey          webhook key
     * @param content             消息内容
     * @param mentionedList       要 @ 的成员 userid 列表，可为空
     * @param mentionedMobileList 要 @ 的成员手机号列表，可为空
     * @return 发送结果
     */
    public WeComRobotResponse sendText(String webhookKey, String content, List<String> mentionedList,
            List<String> mentionedMobileList) {
        return send(webhookKey, new WeComTextMessage(content, mentionedList, mentionedMobileList));
    }

    /**
     * 发送不带 @ 的 text 消息
     *
     * @param webhookKey webhook key
     * @param content    消息内容
     * @return 发送结果
     */
    public WeComRobotResponse sendText(String webhookKey, String content) {
        return send(webhookKey, new WeComTextMessage(content));
    }

    /**
     * 发送 markdown 消息
     *
     * @param webhookKey webhook key
     * @param content    markdown 内容
     * @return 发送结果
     */
    public WeComRobotResponse sendMarkdown(String webhookKey, String content) {
        return send(webhookKey, new WeComMarkdownMessage(content));
    }

    /**
     * 发送 markdown_v2 消息
     *
     * @param webhookKey webhook key
     * @param content    markdown_v2 内容
     * @return 发送结果
     */
    public WeComRobotResponse sendMarkdownV2(String webhookKey, String content) {
        return send(webhookKey, new WeComMarkdownV2Message(content));
    }

    /**
     * 发送 image 消息
     *
     * @param webhookKey webhook key
     * @param imageBytes 图片字节
     * @return 发送结果
     */
    public WeComRobotResponse sendImage(String webhookKey, byte[] imageBytes) {
        return send(webhookKey, new WeComImageMessage(imageBytes));
    }

    /**
     * 发送 news 图文消息
     *
     * @param webhookKey webhook key
     * @param articles   文章列表
     * @return 发送结果
     */
    public WeComRobotResponse sendNews(String webhookKey, List<WeComNewsArticle> articles) {
        return send(webhookKey, new WeComNewsMessage(articles));
    }

    /**
     * 发送 file 消息（media_id 由 {@link #uploadMedia} 获取）
     *
     * @param webhookKey webhook key
     * @param mediaId    上传接口返回的 media_id
     * @return 发送结果
     */
    public WeComRobotResponse sendFile(String webhookKey, String mediaId) {
        return send(webhookKey, WeComMediaMessage.file(mediaId));
    }

    /**
     * 发送 voice 消息（media_id 由 {@link #uploadMedia} 获取）
     *
     * @param webhookKey webhook key
     * @param mediaId    上传接口返回的 media_id
     * @return 发送结果
     */
    public WeComRobotResponse sendVoice(String webhookKey, String mediaId) {
        return send(webhookKey, WeComMediaMessage.voice(mediaId));
    }

    /**
     * 发送模板卡片消息
     *
     * @param webhookKey webhook key
     * @param message    模板卡片消息
     * @return 发送结果
     */
    public WeComRobotResponse sendTemplateCard(String webhookKey, WeComTemplateCardMessage message) {
        return send(webhookKey, message);
    }

    /**
     * 上传文件或语音，取得 media_id（有效期 3 天）
     *
     * @param webhookKey webhook key
     * @param type       file 或 voice
     * @param bytes      文件字节
     * @param fileName   文件名
     * @return 上传结果；成功时 mediaId 有值
     */
    public WeComRobotResponse uploadMedia(String webhookKey, String type, byte[] bytes, String fileName) {
        if (WeComTextUtils.isBlank(webhookKey)) {
            return WeComRobotResponse.fail(-1, "Webhook Key 未配置");
        }
        if (!WeComMediaMessage.TYPE_FILE.equals(type) && !WeComMediaMessage.TYPE_VOICE.equals(type)) {
            return WeComRobotResponse.fail(-1, "上传类型只能是 file 或 voice");
        }
        if (bytes == null || bytes.length <= 5) {
            return WeComRobotResponse.fail(-1, "上传文件必须大于 5 字节");
        }
        long limit = WeComMediaMessage.TYPE_VOICE.equals(type)
                ? WeComMediaMessage.MAX_VOICE_BYTES : WeComMediaMessage.MAX_FILE_BYTES;
        if (bytes.length > limit) {
            return WeComRobotResponse.fail(-1, WeComMediaMessage.TYPE_VOICE.equals(type)
                    ? "语音不能超过 2MB" : "文件不能超过 20MB");
        }
        String key = webhookKey.trim();
        String safeName = StrUtil.blankToDefault(fileName, type.equals(WeComMediaMessage.TYPE_VOICE)
                ? "voice.amr" : "file.bin");
        try {
            String url = UrlBuilder.of(UPLOAD_URL)
                    .addQuery("key", key)
                    .addQuery("type", type)
                    .build();
            try (HttpResponse response = HttpRequest.post(url)
                    .form("media", bytes, safeName)
                    .timeout(TIMEOUT_MS)
                    .execute()) {
                WeComRobotResponse parsed = parse(response.body());
                if (parsed.isSuccess()) {
                    log.info("企业微信媒体上传成功：key={}, type={}, mediaId={}",
                            WeComTextUtils.maskKey(key), type, parsed.getMediaId());
                }
                return parsed;
            }
        } catch (Exception e) {
            // 不打印异常对象：hutool 的异常消息可能带完整 URL（含 key）
            log.warn("企业微信媒体上传失败：key={}, type={}, error={}",
                    WeComTextUtils.maskKey(key), type, e.getClass().getSimpleName());
            return WeComRobotResponse.fail(-1, "企业微信媒体上传失败");
        }
    }

    /**
     * 解析企业微信响应
     *
     * @param body 响应体
     * @return 解析结果
     */
    private WeComRobotResponse parse(String body) {
        try {
            WeComRobotResponse response = JSON.parseObject(body, WeComRobotResponse.class);
            if (response == null) {
                return WeComRobotResponse.fail(-1, "企业微信返回内容为空");
            }
            response.refreshSuccess();
            if (!response.isSuccess()) {
                log.warn("企业微信返回失败：errcode={}, errmsg={}", response.getErrcode(), response.getErrmsg());
            }
            return response;
        } catch (Exception e) {
            log.warn("企业微信响应无法解析：{}", body, e);
            return WeComRobotResponse.fail(-1, "企业微信响应无法解析");
        }
    }

    /**
     * 按 webhook key 做分钟级限流
     *
     * @param key webhook key
     * @return 允许发送返回 true；超限或 Redis 不可用时按允许处理（Redis 异常不能阻断通知）
     */
    private boolean tryAcquireRateLimit(String key) {
        try {
            String rateKey = RATE_KEY_PREFIX + DigestUtil.sha256Hex(key);
            Long current = stringRedisTemplate.opsForValue().increment(rateKey);
            // 每次写入后都续期，避免进程在 increment 与 expire 之间中断留下无 TTL 的键
            stringRedisTemplate.expire(rateKey, 1, TimeUnit.MINUTES);
            return current == null || current <= RATE_LIMIT_PER_MINUTE;
        } catch (Exception e) {
            log.warn("企业微信限流检查失败，继续发送：key={}", WeComTextUtils.maskKey(key));
            return true;
        }
    }
}
