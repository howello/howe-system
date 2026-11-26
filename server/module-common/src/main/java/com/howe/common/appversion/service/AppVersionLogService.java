package com.howe.common.appversion.service;

import com.howe.common.appversion.domain.AppReleaseChannel;

/** 发布日志润色的可选扩展。 */
public interface AppVersionLogService {
    /** 生成指定版本的发布说明。 */
    String polish(AppReleaseChannel channel, String source, String versionName, Integer versionCode);
}
