package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信 file / voice 消息
 *
 * <p>两种类型都必须先调用 {@link WeComRobotClient#uploadMedia(String, String, byte[], String)}
 * 取得 {@code media_id}（有效期 3 天），再构造本消息发送。文件上限 20MB；
 * 语音上限 2MB、时长不超过 60 秒且仅支持 AMR。</p>
 *
 * @author howe
 */
public class WeComMediaMessage implements WeComMessage {

    /** 消息类型：文件 */
    public static final String TYPE_FILE = "file";

    /** 消息类型：语音 */
    public static final String TYPE_VOICE = "voice";

    /** 文件大小上限（字节） */
    public static final long MAX_FILE_BYTES = 20 * 1024 * 1024L;

    /** 语音大小上限（字节） */
    public static final long MAX_VOICE_BYTES = 2 * 1024 * 1024L;

    /** 消息类型：file 或 voice */
    private final String type;

    /** 上传接口返回的 media_id */
    private final String mediaId;

    /**
     * 构造 file / voice 消息
     *
     * @param type    消息类型，file 或 voice
     * @param mediaId 上传接口返回的 media_id
     */
    public WeComMediaMessage(String type, String mediaId) {
        this.type = type;
        this.mediaId = mediaId;
    }

    /**
     * 构造文件消息
     *
     * @param mediaId 上传接口返回的 media_id
     * @return 文件消息
     */
    public static WeComMediaMessage file(String mediaId) {
        return new WeComMediaMessage(TYPE_FILE, mediaId);
    }

    /**
     * 构造语音消息
     *
     * @param mediaId 上传接口返回的 media_id
     * @return 语音消息
     */
    public static WeComMediaMessage voice(String mediaId) {
        return new WeComMediaMessage(TYPE_VOICE, mediaId);
    }

    @Override
    public String msgType() {
        return type;
    }

    @Override
    public JSONObject payload() {
        JSONObject body = new JSONObject();
        body.put("media_id", mediaId);
        return body;
    }

    @Override
    public String validate() {
        if (!TYPE_FILE.equals(type) && !TYPE_VOICE.equals(type)) {
            return "media 消息类型只能是 file 或 voice";
        }
        return WeComTextUtils.isBlank(mediaId) ? "media_id 不能为空，请先上传文件" : null;
    }

    public String getType() {
        return type;
    }

    public String getMediaId() {
        return mediaId;
    }
}
