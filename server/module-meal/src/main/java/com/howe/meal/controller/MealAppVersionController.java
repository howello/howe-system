package com.howe.meal.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.Log;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.constant.HttpStatus;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.common.enums.LimitType;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.domain.dto.AppVersionLogPolishBody;
import com.howe.meal.service.IMealAppVersionService;
import com.howe.meal.service.MealAppVersionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点餐-App 版本 控制层
 *
 * <p>只做 wgt 应用资源热更新：管理端维护并发布版本，点餐端启动时调用 {@code /check} 检测。
 * 检测接口匿名可访问，仅返回版本元数据与资源包地址，不含整包（apk/ipa）升级地址。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-App 版本", description = "wgt 资源包版本管理与客户端热更新检测")
@RestController
@RequestMapping("/meal/app/version")
@RequiredArgsConstructor
public class MealAppVersionController extends BaseController {

    private final IMealAppVersionService mealAppVersionService;

    private final MealAppVersionLogService mealAppVersionLogService;

    /** 脚本发布用的令牌请求头 */
    private static final String PUBLISH_TOKEN_HEADER = "X-Publish-Token";

    @Operation(summary = "查询版本列表")
    @GetMapping("/list")
    public TableDataInfo list(MealAppVersion mealAppVersion) {
        startPage();
        List<MealAppVersion> list = mealAppVersionService.selectMealAppVersionList(mealAppVersion);
        return getDataTable(list);
    }

    /**
     * 客户端热更新检测
     *
     * <p>匿名可访问：App 启动时即可调用，不依赖登录态。客户端需同时上报版本名称与版本号。</p>
     */
    @Operation(summary = "检测是否有新版本", description = "客户端上报当前版本名称与版本号，返回 wgt 更新信息")
    @Anonymous
    @GetMapping("/check")
    public AjaxResult check(
            @Parameter(description = "客户端当前版本名称") @RequestParam(value = "versionName", required = false) String versionName,
            @Parameter(description = "客户端当前版本序号") @RequestParam(value = "versionCode", required = false) Integer versionCode) {
        return success(mealAppVersionService.checkUpdate(versionName, versionCode));
    }

    @Operation(summary = "获取版本详情")
    @GetMapping(value = "/{versionId}")
    public AjaxResult getInfo(@Parameter(description = "版本记录ID", required = true)
            @PathVariable("versionId") Long versionId) {
        return success(mealAppVersionService.selectMealAppVersionById(versionId));
    }

    @Operation(summary = "上传 wgt 资源包", description = "上传 .wgt 文件到对象存储（R2），返回可访问地址")
    @PreAuthorize("@ss.hasPermi('meal:appVersion:upload')")
    @PostMapping("/upload")
    public AjaxResult uploadWgt(MultipartFile file) {
        try {
            String url = mealAppVersionService.uploadWgt(file);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("size", file.getSize());
            data.put("fileName", file.getOriginalFilename());
            return AjaxResult.success(data);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Operation(summary = "新增版本")
    @PreAuthorize("@ss.hasPermi('meal:appVersion:add')")
    @Log(title = "点餐App版本", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealAppVersion mealAppVersion) {
        return toAjax(mealAppVersionService.insertMealAppVersion(mealAppVersion));
    }

    @Operation(summary = "修改版本")
    @PreAuthorize("@ss.hasPermi('meal:appVersion:edit')")
    @Log(title = "点餐App版本", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MealAppVersion mealAppVersion) {
        return toAjax(mealAppVersionService.updateMealAppVersion(mealAppVersion));
    }

    @Operation(summary = "删除版本")
    @PreAuthorize("@ss.hasPermi('meal:appVersion:remove')")
    @Log(title = "点餐App版本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{versionIds}")
    public AjaxResult remove(@Parameter(description = "版本记录ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] versionIds) {
        return toAjax(mealAppVersionService.deleteMealAppVersionByIds(versionIds));
    }

    /**
     * 脚本发布：上传 wgt 到 R2 并新建一条已发布的版本记录
     *
     * <p>匿名放行，用请求头 {@code X-Publish-Token} 与参数 {@code meal.app.publishToken} 做常量时间比较。
     * 令牌未配置时一律拒绝——不能因为参数缺失就变成无鉴权的上传口。只授权「上传 + 新建记录」，
     * 不提供读取、修改、停用、删除能力。</p>
     */
    @Operation(summary = "发布 wgt 版本（脚本用）",
            description = "用发布令牌鉴权，一次请求完成「上传 wgt 到 R2 + 新建版本记录」")
    @Anonymous
    @PostMapping("/publish")
    public AjaxResult publish(
            @Parameter(description = "发布令牌") @RequestHeader(value = PUBLISH_TOKEN_HEADER, required = false) String publishToken,
            @Parameter(description = "wgt 资源包") @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "版本名称") @RequestParam(value = "versionName", required = false) String versionName,
            @Parameter(description = "版本序号") @RequestParam(value = "versionCode", required = false) Integer versionCode,
            @Parameter(description = "更新说明") @RequestParam(value = "updateLog", required = false) String updateLog,
            @Parameter(description = "是否强制更新（0/1）") @RequestParam(value = "forceUpdate", required = false) String forceUpdate) {
        AjaxResult tokenError = assertPublishToken(publishToken);
        if (tokenError != null) {
            return tokenError;
        }
        try {
            MealAppVersion version = mealAppVersionService.publishWgt(file, versionName, versionCode, updateLog, forceUpdate);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("versionId", version.getVersionId());
            data.put("versionName", version.getVersionName());
            data.put("versionCode", version.getVersionCode());
            data.put("wgtUrl", version.getWgtUrl());
            data.put("wgtSize", version.getWgtSize());
            data.put("fileName", version.getFileName());
            data.put("forceUpdate", version.getForceUpdate());
            return AjaxResult.success(data);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 润色发版日志（脚本用）
     *
     * <p>与发布接口共用 {@code X-Publish-Token} 鉴权：CI 把本次提交摘要发来，返回家庭用户可读的
     * 更新说明；润色结果不落库，由脚本随后作为 {@code updateLog} 交给发布接口。
     * 调用失败时脚本回退原始摘要，不影响发版。</p>
     */
    @Operation(summary = "润色发版日志（脚本用）",
            description = "用发布令牌鉴权，把提交摘要交给 AI 提炼成用户可读的更新说明；结果不落库")
    @Anonymous
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/polish-log")
    public AjaxResult polishLog(
            @Parameter(description = "发布令牌") @RequestHeader(value = PUBLISH_TOKEN_HEADER, required = false) String publishToken,
            @RequestBody AppVersionLogPolishBody body) {
        AjaxResult tokenError = assertPublishToken(publishToken);
        if (tokenError != null) {
            return tokenError;
        }
        try {
            String updateLog = mealAppVersionLogService.polish(
                    body == null ? null : body.getSource(),
                    body == null ? null : body.getVersionName(),
                    body == null ? null : body.getVersionCode());
            return AjaxResult.success(updateLog);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 校验发布令牌
     *
     * @param publishToken 请求头里的令牌
     * @return 校验通过返回 {@code null}，否则返回应直接回给调用方的错误结果
     */
    private AjaxResult assertPublishToken(String publishToken) {
        String expected = ConfigUtils.getString(ConfigConstants.MEAL_APP_PUBLISH_TOKEN);
        if (StringUtils.isEmpty(expected)) {
            return AjaxResult.error("发布令牌未配置：请在「系统管理 > 参数设置」配置参数 "
                    + ConfigConstants.MEAL_APP_PUBLISH_TOKEN);
        }
        // 常量时间比较，避免通过响应时间推断令牌
        if (StringUtils.isEmpty(publishToken) || !MessageDigest.isEqual(
                publishToken.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            return AjaxResult.error(HttpStatus.UNAUTHORIZED, "发布令牌无效");
        }
        return null;
    }
}
