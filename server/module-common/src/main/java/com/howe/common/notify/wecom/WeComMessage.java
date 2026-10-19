package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信群机器人消息
 *
 * <p>所有消息类型实现本接口，由 {@link WeComRobotClient#send(String, WeComMessage)} 统一发送。
 * {@link #msgType()} 同时作为请求体中消息对象的键名（如 text、markdown、template_card）。</p>
 *
 * @author howe
 */
public interface WeComMessage {

    /**
     * 消息类型，与官方 {@code msgtype} 取值一致
     *
     * @return 消息类型
     */
    String msgType();

    /**
     * 消息体，对应请求体中与 {@link #msgType()} 同名的对象
     *
     * @return 消息体
     */
    JSONObject payload();

    /**
     * 发送前校验消息参数
     *
     * @return 校验不通过时的可读原因；通过时返回 null
     */
    default String validate() {
        return null;
    }
}
