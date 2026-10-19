package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信 template_card 模板卡片消息
 *
 * <p>本客户端支持官方两种常用卡片：{@code text_notice} 文本通知卡片与
 * {@code news_notice} 图文展示卡片。卡片字段较多，调用方可通过 {@link #put(String, Object)}
 * 补充官方字段；{@code card_type} 与 {@code card_action} 由构造时确定。</p>
 *
 * @author howe
 */
public class WeComTemplateCardMessage implements WeComMessage {

    /** 卡片类型：文本通知 */
    public static final String CARD_TYPE_TEXT_NOTICE = "text_notice";

    /** 卡片类型：图文展示 */
    public static final String CARD_TYPE_NEWS_NOTICE = "news_notice";

    /** 卡片类型 */
    private final String cardType;

    /** 卡片动作：{@code type=1} 跳转 URL */
    private final String actionUrl;

    /** 卡片字段 */
    private final JSONObject card = new JSONObject();

    /**
     * 构造模板卡片消息
     *
     * @param cardType  卡片类型，text_notice 或 news_notice
     * @param actionUrl 点击卡片后打开的链接
     */
    public WeComTemplateCardMessage(String cardType, String actionUrl) {
        this.cardType = cardType;
        this.actionUrl = actionUrl;
        this.card.put("card_type", cardType);
    }

    /**
     * 写入卡片字段
     *
     * @param key   字段名
     * @param value 字段值
     * @return 当前对象，便于链式调用
     */
    public WeComTemplateCardMessage put(String key, Object value) {
        card.put(key, value);
        return this;
    }

    /**
     * 设置主标题
     *
     * @param title 标题
     * @param desc  描述，可为空
     * @return 当前对象
     */
    public WeComTemplateCardMessage mainTitle(String title, String desc) {
        JSONObject mainTitle = new JSONObject();
        mainTitle.put("title", title);
        if (desc != null && !desc.isEmpty()) {
            mainTitle.put("desc", desc);
        }
        card.put("main_title", mainTitle);
        return this;
    }

    /**
     * 设置副标题文本
     *
     * @param text 副标题
     * @return 当前对象
     */
    public WeComTemplateCardMessage subTitleText(String text) {
        card.put("sub_title_text", text);
        return this;
    }

    @Override
    public String msgType() {
        return "template_card";
    }

    @Override
    public JSONObject payload() {
        JSONObject payload = new JSONObject();
        payload.putAll(card);
        JSONObject action = new JSONObject();
        action.put("type", 1);
        action.put("url", actionUrl);
        payload.put("card_action", action);
        return payload;
    }

    @Override
    public String validate() {
        if (!CARD_TYPE_TEXT_NOTICE.equals(cardType) && !CARD_TYPE_NEWS_NOTICE.equals(cardType)) {
            return "模板卡片类型只能是 text_notice 或 news_notice";
        }
        if (WeComTextUtils.isBlank(actionUrl)) {
            return "模板卡片跳转链接不能为空";
        }
        JSONObject mainTitle = card.getJSONObject("main_title");
        boolean hasMainTitle = mainTitle != null && !WeComTextUtils.isBlank(mainTitle.getString("title"));
        boolean hasSubTitle = !WeComTextUtils.isBlank(card.getString("sub_title_text"));
        if (!hasMainTitle && !hasSubTitle) {
            return "模板卡片必须提供 main_title.title 或 sub_title_text";
        }
        if (CARD_TYPE_NEWS_NOTICE.equals(cardType)) {
            if (!hasMainTitle) {
                return "图文展示卡片必须提供 main_title";
            }
            if (card.get("card_image") == null) {
                return "图文展示卡片必须提供 card_image";
            }
        }
        return null;
    }

    public String getCardType() {
        return cardType;
    }

    public String getActionUrl() {
        return actionUrl;
    }
}
