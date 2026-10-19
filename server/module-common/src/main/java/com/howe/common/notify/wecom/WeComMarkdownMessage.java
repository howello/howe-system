package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信 markdown 消息
 *
 * <p>内容上限 4096 字节；支持标题、加粗、链接、行内代码、引用、字体颜色，
 * 可用 {@code <@userid>} 提醒成员。</p>
 *
 * @author howe
 */
public class WeComMarkdownMessage implements WeComMessage {

    /** 官方内容上限（字节） */
    public static final int MAX_CONTENT_BYTES = 4096;

    /** 实际发送时使用的上限（字节），留出余量 */
    public static final int SAFE_CONTENT_BYTES = 4000;

    /** markdown 内容 */
    private final String content;

    /**
     * 构造 markdown 消息
     *
     * @param content markdown 内容
     */
    public WeComMarkdownMessage(String content) {
        this.content = content;
    }

    @Override
    public String msgType() {
        return "markdown";
    }

    @Override
    public JSONObject payload() {
        JSONObject body = new JSONObject();
        body.put("content", WeComTextUtils.truncateUtf8(content, SAFE_CONTENT_BYTES));
        return body;
    }

    @Override
    public String validate() {
        return WeComTextUtils.isBlank(content) ? "markdown 内容不能为空" : null;
    }

    public String getContent() {
        return content;
    }
}
