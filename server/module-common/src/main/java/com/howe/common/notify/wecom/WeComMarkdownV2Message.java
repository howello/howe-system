package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信 markdown_v2 消息
 *
 * <p>内容上限 4096 字节；支持标题、列表、表格、代码块、分割线等，
 * 不支持字体颜色与成员提醒。低于客户端 4.1.36 的版本可能显示为纯文本。</p>
 *
 * @author howe
 */
public class WeComMarkdownV2Message implements WeComMessage {

    /** 官方内容上限（字节） */
    public static final int MAX_CONTENT_BYTES = 4096;

    /** 实际发送时使用的上限（字节），留出余量 */
    public static final int SAFE_CONTENT_BYTES = 4000;

    /** markdown_v2 内容 */
    private final String content;

    /**
     * 构造 markdown_v2 消息
     *
     * @param content markdown_v2 内容
     */
    public WeComMarkdownV2Message(String content) {
        this.content = content;
    }

    @Override
    public String msgType() {
        return "markdown_v2";
    }

    @Override
    public JSONObject payload() {
        JSONObject body = new JSONObject();
        body.put("content", WeComTextUtils.truncateUtf8(content, SAFE_CONTENT_BYTES));
        return body;
    }

    @Override
    public String validate() {
        return WeComTextUtils.isBlank(content) ? "markdown_v2 内容不能为空" : null;
    }

    public String getContent() {
        return content;
    }
}
