package com.howe.web.controller.system;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.poi.ExcelUtil;
import com.howe.system.domain.SysConfig;
import com.howe.system.service.ISysConfigService;

/**
 * 参数配置 信息操作处理
 * 
 * @author howe
 */
@Tag(name = "参数配置", description = "系统参数配置的查询、维护与缓存刷新")
@RestController
@RequestMapping("/system/config")
public class SysConfigController extends BaseController
{
    @Autowired
    private ISysConfigService configService;

    /**
     * 获取参数配置列表
     */
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @Operation(summary = "查询参数配置列表", description = "分页查询系统参数配置；传 uncategorized=true 只查未分类")
    @GetMapping("/list")
    public TableDataInfo list(SysConfig config,
            @Parameter(description = "是否只查未分类（空 / NULL / 纯空白的 param_type）")
            @RequestParam(required = false) Boolean uncategorized)
    {
        // SysConfig 继承 BaseEntity，顶层查询参数不会自动落进 params，这里显式搬运
        if (Boolean.TRUE.equals(uncategorized))
        {
            config.getParams().put("uncategorized", Boolean.TRUE);
        }
        startPage();
        List<SysConfig> list = configService.selectConfigList(config);
        return getDataTable(list);
    }

    /**
     * 查询全量参数分类
     *
     * <p>供参数管理页的分类下拉使用。结果独立于分页与搜索条件，
     * 因此不会因为翻页而丢失其它页出现过的分类。</p>
     */
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @Operation(summary = "查询全量参数分类", description = "返回全表出现过的分类；存在空 / NULL / 纯空白分类时末尾附带「未分类」")
    @GetMapping("/paramTypes")
    public AjaxResult paramTypes()
    {
        return success(configService.selectParamTypes());
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:config:export')")
    @Operation(summary = "导出参数配置", description = "按查询条件导出参数配置 Excel")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysConfig config)
    {
        List<SysConfig> list = configService.selectConfigList(config);
        ExcelUtil<SysConfig> util = new ExcelUtil<SysConfig>(SysConfig.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:config:query')")
    @Operation(summary = "获取参数配置详细信息", description = "根据参数编号查询参数配置详情")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@Parameter(description = "参数编号") @PathVariable Long configId)
    {
        return success(configService.selectConfigById(configId));
    }

    /**
     * 根据参数键名查询参数值
     *
     * <p>
     * 2026-10-21 渗透事件的实际泄露出口：此前没有任何权限注解，任意登录用户
     * （攻击者用的是 ry 演示账号）都能读到 R2 密钥、GitHub PAT 等明文。
     * 现在补上 {@code system:config:query}，且敏感键只返回掩码。
     * </p>
     */
    @PreAuthorize("@ss.hasPermi('system:config:query')")
    @Operation(summary = "根据参数键名查询参数值", description = "供前端按键名读取单个参数值；敏感参数只返回掩码")
    @GetMapping(value = "/configKey/{configKey}")
    public AjaxResult getConfigKey(@Parameter(description = "参数键名") @PathVariable String configKey)
    {
        return success(configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:add')")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增参数配置", description = "参数键名不可重复；敏感参数的值加密入库")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysConfig config)
    {
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        // 值字段去掉了 @NotBlank（编辑时留空表示不修改），新增场景在这里兜底
        if (StringUtils.isEmpty(config.getConfigValue()))
        {
            return error("参数键值不能为空");
        }
        config.setCreateBy(getUsername());
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改参数配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改参数配置", description = "参数键名不可与其它参数重复")
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysConfig config)
    {
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setUpdateBy(getUsername());
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 删除参数配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @Operation(summary = "删除参数配置", description = "支持传入多个参数编号批量删除")
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@Parameter(description = "参数编号数组") @PathVariable Long[] configIds)
    {
        configService.deleteConfigByIds(configIds);
        return success();
    }

    /**
     * 刷新参数缓存
     */
    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @Operation(summary = "刷新参数缓存", description = "清空并重建 Redis 中的参数配置缓存")
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache()
    {
        configService.resetConfigCache();
        return success();
    }
}
