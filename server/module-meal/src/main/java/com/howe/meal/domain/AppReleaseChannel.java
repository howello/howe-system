package com.howe.meal.domain;

import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 共用版本管理的应用渠道，每个渠道独立匹配应用标识和发布凭证。 */
@Getter
@RequiredArgsConstructor
public enum AppReleaseChannel {

    MEAL("meal", "吃了么", "__UNI__091B4F4", "meal/app", ConfigConstants.MEAL_APP_PUBLISH_TOKEN),
    PREGNANCY("pregnancy", "宝宝", "__UNI__B95B91A", "pregnancy/app", ConfigConstants.PREGNANCY_APP_PUBLISH_TOKEN);

    private final String key;
    private final String displayName;
    private final String appId;
    private final String directory;
    private final String tokenKey;

    /** 无渠道参数的历史客户端仍走点餐。 */
    public static AppReleaseChannel resolve(String key) {
        if (key == null || key.isBlank() || MEAL.key.equals(key)) {
            return MEAL;
        }
        if (PREGNANCY.key.equals(key)) {
            return PREGNANCY;
        }
        throw new ServiceException("未知应用渠道");
    }
}
