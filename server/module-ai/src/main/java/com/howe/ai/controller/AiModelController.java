package com.howe.ai.controller;

import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.dto.AiModelImportRequest;
import com.howe.ai.service.IAiModelService;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;

/**
 * AI 模型 控制层
 *
 * @author howe
 */
@Slf4j
@Tag(name = "AI 模型管理", description = "渠道下的具体模型，按能力类型区分")
@RestController
@RequestMapping("/ai/model")
@RequiredArgsConstructor
public class AiModelController extends BaseController
{
    private final IAiModelService aiModelService;

    /**
     * 查询模型列表
     */
    @Operation(summary = "查询模型列表", description = "分页查询，联表带出归属渠道名称与服务商标识")
    @PreAuthorize("@ss.hasPermi('ai:model:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiModel aiModel)
    {
        startPage();
        List<AiModel> list = aiModelService.selectAiModelList(aiModel);
        return getDataTable(list);
    }

    /**
     * 获取模型详情
     */
    @Operation(summary = "获取模型详情")
    @PreAuthorize("@ss.hasPermi('ai:model:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@Parameter(description = "模型ID", required = true) @PathVariable("id") Long id)
    {
        return success(aiModelService.selectAiModelById(id));
    }

    /**
     * 新增模型
     */
    @Operation(summary = "新增模型")
    @PreAuthorize("@ss.hasPermi('ai:model:add')")
    @Log(title = "AI 模型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody AiModel aiModel)
    {
        log.debug("新增模型入参：{}", aiModel);
        return toAjax(aiModelService.insertAiModel(aiModel));
    }

    /**
     * 修改模型
     */
    @Operation(summary = "修改模型")
    @PreAuthorize("@ss.hasPermi('ai:model:edit')")
    @Log(title = "AI 模型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody AiModel aiModel)
    {
        log.debug("修改模型入参：{}", aiModel);
        return toAjax(aiModelService.updateAiModel(aiModel));
    }

    /**
     * 删除模型
     */
    @Operation(summary = "删除模型", description = "被场景路由引用时拒绝删除")
    @PreAuthorize("@ss.hasPermi('ai:model:remove')")
    @Log(title = "AI 模型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@Parameter(description = "模型ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] ids)
    {
        return toAjax(aiModelService.deleteAiModelByIds(ids));
    }

    /**
     * 启用或停用模型
     */
    @Operation(summary = "启用或停用模型", description = "停用后该模型不参与路由与降级")
    @PreAuthorize("@ss.hasPermi('ai:model:edit')")
    @Log(title = "AI 模型", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@Parameter(description = "模型ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "0停用 1启用", required = true) @RequestParam Integer enabled)
    {
        return toAjax(aiModelService.updateStatus(id, enabled));
    }

    /**
     * 查询厂商模型列表
     */
    @Operation(summary = "查询厂商模型列表",
            description = "调用服务商的 models 接口取回模型清单，标记出该渠道下已配置的项；协议不支持时提示手动添加")
    @PreAuthorize("@ss.hasPermi('ai:model:add')")
    @GetMapping("/remote")
    public AjaxResult remote(@Parameter(description = "渠道ID", required = true) @RequestParam Long channelId)
    {
        return success(aiModelService.listRemoteModels(channelId));
    }

    /**
     * 批量导入厂商模型
     */
    @Operation(summary = "批量导入厂商模型",
            description = "已存在的模型名自动跳过；返回实际新增条数，0 表示所选模型都已在渠道下配置")
    @PreAuthorize("@ss.hasPermi('ai:model:add')")
    @Log(title = "AI 模型", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public AjaxResult batch(@Valid @RequestBody AiModelImportRequest request)
    {
        return success(aiModelService.importModels(request));
    }
}
