package com.howe.web.controller.tool;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.AppReleaseChannel;
import com.howe.system.domain.AppReleaseNotifyConfig;
import com.howe.system.service.AppReleaseNotifyConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 在 App 版本管理权限范围内管理每个应用的发版机器人。 */
@Tag(name = "App 发版通知", description = "按应用管理企业微信群机器人配置")
@RestController
@RequestMapping("/tool/app-release/notify/{appKey}")
@PreAuthorize("@ss.hasPermi('tool:appVersion:edit') && "
        + "(#appKey != 'pregnancy' || @ss.hasPermi('tool:appVersion:pregnancy'))")
@RequiredArgsConstructor
public class AppReleaseNotifyController extends BaseController {

    private final AppReleaseNotifyConfigService configService;

    /** 列出指定应用的发版通知配置。 */
    @Operation(summary = "查询应用的发版机器人配置")
    @GetMapping
    public AjaxResult list(@PathVariable String appKey) {
        return success(configService.list(AppReleaseChannel.resolve(appKey).getKey()));
    }

    /** 为指定应用添加一条发版机器人配置。 */
    @Operation(summary = "添加发版机器人")
    @Log(title = "App 发版通知", businessType = BusinessType.INSERT, excludeParamNames = "webhookKey")
    @PostMapping
    public AjaxResult add(@PathVariable String appKey, @Valid @RequestBody AppReleaseNotifyConfig config) {
        return toAjax(configService.add(AppReleaseChannel.resolve(appKey).getKey(), config));
    }

    /** 修改机器人备注、开关或凭证。 */
    @Operation(summary = "修改发版机器人", description = "Webhook Key 留空表示继续使用原凭证")
    @Log(title = "App 发版通知", businessType = BusinessType.UPDATE, excludeParamNames = "webhookKey")
    @PutMapping("/{configId}")
    public AjaxResult update(@PathVariable String appKey, @PathVariable Long configId,
            @Valid @RequestBody AppReleaseNotifyConfig config) {
        return toAjax(configService.update(AppReleaseChannel.resolve(appKey).getKey(), configId, config));
    }

    /** 删除指定应用下的机器人。 */
    @Operation(summary = "删除发版机器人")
    @Log(title = "App 发版通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configId}")
    public AjaxResult delete(@PathVariable String appKey, @PathVariable Long configId) {
        return toAjax(configService.delete(AppReleaseChannel.resolve(appKey).getKey(), configId));
    }
}
