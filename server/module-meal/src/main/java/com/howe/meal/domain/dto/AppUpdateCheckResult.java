package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * App 热更新检测结果
 *
 * <p>对应 DCloud 文章约定的检测接口返回体。只做 wgt 资源热更新，因此<b>不包含</b>整包
 * （apk/ipa）下载地址或应用商店链接。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "App 热更新检测结果")
public class AppUpdateCheckResult {

    @Schema(description = "是否有更新", example = "true")
    private Boolean update;

    @Schema(description = "最新版本名称", example = "1.0.4")
    private String versionName;

    @Schema(description = "最新版本序号", example = "107")
    private Integer versionCode;

    @Schema(description = "wgt 资源包下载地址")
    private String wgtUrl;

    @Schema(description = "wgt 资源包字节大小")
    private Long wgtSize;

    @Schema(description = "是否强制更新", example = "false")
    private Boolean forceUpdate;

    @Schema(description = "更新说明")
    private String updateLog;

    /**
     * 无更新时的返回体：只带 update=false，其余字段为 null。
     */
    public static AppUpdateCheckResult none() {
        AppUpdateCheckResult result = new AppUpdateCheckResult();
        result.setUpdate(Boolean.FALSE);
        return result;
    }
}
