package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信 text 消息
 *
 * <p>内容上限 2048 字节；发送时截断到 {@value #MAX_CONTENT_BYTES} 字节留出余量。
 * 支持按成员 userid 或手机号 @ 人，两个列表都可为空。</p>
 *
 * @author howe
 */
public class WeComTextMessage implements WeComMessage {

    /** 官方内容上限（字节） */
    public static final int MAX_CONTENT_BYTES = 2048;

    /** 实际发送时使用的上限（字节），留出余量 */
    public static final int SAFE_CONTENT_BYTES = 2000;

    /** 消息内容 */
    private final String content;

    /** 要 @ 的成员 userid 列表 */
    private final List<String> mentionedList;

    /** 要 @ 的成员手机号列表 */
    private final List<String> mentionedMobileList;

    /**
     * 构造 text 消息
     *
     * @param content             消息内容
     * @param mentionedList       要 @ 的成员 userid 列表，可为空
     * @param mentionedMobileList 要 @ 的成员手机号列表，可为空
     */
    public WeComTextMessage(String content, List<String> mentionedList, List<String> mentionedMobileList) {
        this.content = content;
        this.mentionedList = mentionedList;
        this.mentionedMobileList = mentionedMobileList;
    }

    /**
     * 构造不带 @ 的 text 消息
     *
     * @param content 消息内容
     */
    public WeComTextMessage(String content) {
        this(content, null, null);
    }

    /**
     * 构造按手机号 @ 的 text 消息
     *
     * @param content              消息内容
     * @param mentionedMobileList  手机号列表
     * @return text 消息
     */
    public static WeComTextMessage ofMobiles(String content, List<String> mentionedMobileList) {
        return new WeComTextMessage(content, null, mentionedMobileList);
    }

    @Override
    public String msgType() {
        return "text";
    }

    @Override
    public JSONObject payload() {
        JSONObject text = new JSONObject();
        text.put("content", WeComTextUtils.truncateUtf8(content, SAFE_CONTENT_BYTES));
        if (mentionedList != null && !mentionedList.isEmpty()) {
            text.put("mentioned_list", mentionedList);
        }
        if (mentionedMobileList != null && !mentionedMobileList.isEmpty()) {
            text.put("mentioned_mobile_list", mentionedMobileList);
        }
        return text;
    }

    @Override
    public String validate() {
        return WeComTextUtils.isBlank(content) ? "消息内容不能为空" : null;
    }

    /**
     * 过滤空白并去重手机号
     *
     * @param mobiles 手机号集合
     * @return 过滤后的列表
     */
    public static List<String> normalizeMobiles(List<String> mobiles) {
        List<String> result = new ArrayList<>();
        if (mobiles == null) {
            return result;
        }
        for (String mobile : mobiles) {
            if (WeComTextUtils.isBlank(mobile)) {
                continue;
            }
            String trimmed = mobile.trim();
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public String getContent() {
        return content;
    }

    public List<String> getMentionedList() {
        return mentionedList;
    }

    public List<String> getMentionedMobileList() {
        return mentionedMobileList;
    }
}
