package com.howe.common.notify.wecom;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信 image 消息
 *
 * <p>官方要求原图不超过 2MB，仅支持 JPG、PNG；请求体传 Base64 与原始图片的 MD5。</p>
 *
 * @author howe
 */
public class WeComImageMessage implements WeComMessage {

    /** 官方图片大小上限（字节） */
    public static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024L;

    /** 图片字节 */
    private final byte[] imageBytes;

    /**
     * 构造 image 消息
     *
     * @param imageBytes 图片字节
     */
    public WeComImageMessage(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    @Override
    public String msgType() {
        return "image";
    }

    @Override
    public JSONObject payload() {
        JSONObject image = new JSONObject();
        image.put("base64", Base64.encode(imageBytes));
        image.put("md5", DigestUtil.md5Hex(imageBytes));
        return image;
    }

    @Override
    public String validate() {
        if (imageBytes == null || imageBytes.length == 0) {
            return "图片内容不能为空";
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            return "图片不能超过 2MB";
        }
        return null;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }
}
