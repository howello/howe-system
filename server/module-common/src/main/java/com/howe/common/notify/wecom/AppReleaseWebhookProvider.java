package com.howe.common.notify.wecom;

import java.util.List;

/** 读取应用独立配置的发版通知目的地。 */
public interface AppReleaseWebhookProvider {

    /** 返回指定应用已启用的机器人凭证，仅供内部发送。 */
    List<String> findEnabledKeys(String appKey);
}
