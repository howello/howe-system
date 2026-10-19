package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealWeComConfig;
import com.howe.meal.service.IMealWeComConfigService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 点餐-企业微信通知配置 控制层
 *
 * <p>配置按家庭维护：平台管理员可管理全部家庭，家庭管理员只能管理本家庭。
 * Webhook Key 是凭证，列表与详情返回掩码；新增与修改的 {@code @Log} 排除 key 参数，
 * 避免明文写入操作日志。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-企业微信通知", description = "按家庭维护企业微信群机器人配置与测试发送")
@RestController
@RequestMapping("/meal/wecom")
@RequiredArgsConstructor
public class MealWeComController extends BaseController {

    private final IMealWeComConfigService mealWeComConfigService;

    /**
     * 查询企业微信通知配置列表
     *
     * @param config 查询条件
     * @return 配置列表
     */
    @Operation(summary = "查询企业微信通知配置列表",
            description = "平台管理员返回全部家庭，其他用户只返回本家庭；Webhook Key 为掩码")
    @PreAuthorize("@ss.hasPermi('meal:wecom:list')")
    @GetMapping("/list")
    public TableDataInfo list(MealWeComConfig config) {
        startPage();
        List<MealWeComConfig> list = mealWeComConfigService.selectMealWeComConfigList(config);
        return getDataTable(list);
    }

    /**
     * 获取配置详情
     *
     * @param configId 配置ID
     * @return 配置详情
     */
    @Operation(summary = "获取企业微信通知配置详情", description = "Webhook Key 返回掩码")
    @PreAuthorize("@ss.hasPermi('meal:wecom:query')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@Parameter(description = "配置ID", required = true)
            @PathVariable("configId") Long configId) {
        return success(mealWeComConfigService.selectMealWeComConfigById(configId));
    }

    /**
     * 新增配置
     *
     * @param config 配置
     * @return 结果
     */
    @Operation(summary = "新增企业微信通知配置", description = "每个家庭只能有一条配置；非平台管理员家庭固定为本家庭")
    @PreAuthorize("@ss.hasPermi('meal:wecom:add')")
    @Log(title = "点餐企业微信通知", businessType = BusinessType.INSERT, excludeParamNames = { "webhookKey" })
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealWeComConfig config) {
        return toAjax(mealWeComConfigService.insertMealWeComConfig(config));
    }

    /**
     * 修改配置
     *
     * @param config 配置
     * @return 结果
     */
    @Operation(summary = "修改企业微信通知配置", description = "Webhook Key 留空或回传掩码表示保留原值")
    @PreAuthorize("@ss.hasPermi('meal:wecom:edit')")
    @Log(title = "点餐企业微信通知", businessType = BusinessType.UPDATE, excludeParamNames = { "webhookKey" })
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MealWeComConfig config) {
        return toAjax(mealWeComConfigService.updateMealWeComConfig(config));
    }

    /**
     * 删除配置
     *
     * @param configIds 配置ID数组
     * @return 结果
     */
    @Operation(summary = "删除企业微信通知配置", description = "逻辑删除，支持批量")
    @PreAuthorize("@ss.hasPermi('meal:wecom:remove')")
    @Log(title = "点餐企业微信通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@Parameter(description = "配置ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] configIds) {
        return toAjax(mealWeComConfigService.deleteMealWeComConfigByIds(configIds));
    }

    /**
     * 发送测试消息
     *
     * @param configId 配置ID
     * @return 结果
     */
    @Operation(summary = "测试发送", description = "向该家庭配置的企业微信群发送一条纯文本测试消息；失败返回可读原因")
    @PreAuthorize("@ss.hasPermi('meal:wecom:test')")
    @Log(title = "点餐企业微信通知", businessType = BusinessType.OTHER)
    @PostMapping("/{configId}/test")
    public AjaxResult test(@Parameter(description = "配置ID", required = true)
            @PathVariable("configId") Long configId) {
        return success(mealWeComConfigService.sendTestMessage(configId));
    }
}
