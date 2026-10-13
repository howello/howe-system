package com.howe.meal.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点餐-App 版本对象 meal_app_version
 *
 * <p>一条记录 = 一个已上传到对象存储（R2）的 wgt 应用资源包。客户端启动时上报自己的
 * 「版本名称 + 版本号」，由 {@code MealAppVersionController#check} 按已发布的最新版本判定是否有更新。
 * 只服务 wgt 资源热更新，不承载整包（apk/ipa）升级。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-App 版本（wgt 热更新）")
public class MealAppVersion extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "版本记录ID", example = "1")
    private Long versionId;

    @Schema(description = "版本名称（展示用）", example = "1.0.4")
    @NotBlank(message = "版本名称不能为空")
    @Size(max = 32, message = "版本名称长度不能超过32个字符")
    private String versionName;

    @Schema(description = "版本序号（比较基准，整数递增）", example = "107")
    @NotNull(message = "版本序号不能为空")
    private Integer versionCode;

    @Schema(description = "wgt 资源包在对象存储上的可访问地址")
    @NotBlank(message = "请先上传 wgt 资源包")
    @Size(max = 500, message = "资源包地址长度不能超过500个字符")
    private String wgtUrl;

    @Schema(description = "资源包字节大小", example = "1234567")
    private Long wgtSize;

    @Schema(description = "上传时的原始文件名", example = "__UNI__091B4F4.wgt")
    @Size(max = 255, message = "文件名长度不能超过255个字符")
    private String fileName;

    @Schema(description = "更新说明（换行分隔，客户端弹窗展示）")
    private String updateLog;

    @Schema(description = "是否强制更新（0否 1是）", example = "0")
    private String forceUpdate;

    @Schema(description = "发布状态（0已发布 1停用）", example = "0")
    private String status;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;
}
