package com.howe.meal.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.service.IMealAppVersionService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}
