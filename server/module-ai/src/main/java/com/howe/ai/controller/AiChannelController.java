package com.howe.ai.controller;

import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.provider.AiHealthResult;
import com.howe.ai.service.IAiChannelService;
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
 * AI 渠道 控制层
 *
 * <p>密钥在响应里一律是掩码，编辑时留空表示保留原值。
 * 新增与修改都显式声明 {@code excludeParamNames = "apiKey"}：{@code @Log} 会把入参
 * 序列化进 {@code sys_oper_log.oper_param}，不排除就会把明文密钥写进操作日志。</p>
 *
 * @author howe
 */
@Tag(name = "AI 渠道管理", description = "服务商下的一组密钥，可启停、可测连通性")
@RestController
@RequestMapping("/ai/channel")
@RequiredArgsConstructor
public class AiChannelController extends BaseController
{
    private final IAiChannelService aiChannelService;

    /**
     * 查询服务商下拉选项
     */
    @Operation(summary = "查询服务商下拉选项", description = "返回启用的服务商，供渠道表单选择")
    @PreAuthorize("@ss.hasPermi('ai:channel:list')")
    @GetMapping("/providers")
    public AjaxResult providers()
    {
        return success(aiChannelService.selectProviderOptions());
    }

    /**
     * 查询渠道列表
     */
    @Operation(summary = "查询渠道列表", description = "分页查询，按优先级升序；密钥返回掩码")
    @PreAuthorize("@ss.hasPermi('ai:channel:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiChannel aiChannel)
    {
        startPage();
        List<AiChannel> list = aiChannelService.selectAiChannelList(aiChannel);
        return getDataTable(list);
    }

    /**
     * 获取渠道详情
     */
    @Operation(summary = "获取渠道详情", description = "密钥返回掩码，编辑时留空表示不修改")
    @PreAuthorize("@ss.hasPermi('ai:channel:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@Parameter(description = "渠道ID", required = true) @PathVariable("id") Long id)
    {
        return success(aiChannelService.selectAiChannelById(id));
    }

    /**
     * 新增渠道
     */
    @Operation(summary = "新增渠道", description = "密钥加密后入库")
    @PreAuthorize("@ss.hasPermi('ai:channel:add')")
    @Log(title = "AI 渠道", businessType = BusinessType.INSERT, excludeParamNames = { "apiKey" })
    @PostMapping
    public AjaxResult add(@Valid @RequestBody AiChannel aiChannel)
    {
        return toAjax(aiChannelService.insertAiChannel(aiChannel));
    }

    /**
     * 修改渠道
     */
    @Operation(summary = "修改渠道", description = "密钥留空或传掩码时保留原值")
    @PreAuthorize("@ss.hasPermi('ai:channel:edit')")
    @Log(title = "AI 渠道", businessType = BusinessType.UPDATE, excludeParamNames = { "apiKey" })
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody AiChannel aiChannel)
    {
        return toAjax(aiChannelService.updateAiChannel(aiChannel));
    }

    /**
     * 删除渠道
     */
    @Operation(summary = "删除渠道", description = "渠道下还有模型时拒绝删除")
    @PreAuthorize("@ss.hasPermi('ai:channel:remove')")
    @Log(title = "AI 渠道", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@Parameter(description = "渠道ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] ids)
    {
        return toAjax(aiChannelService.deleteAiChannelByIds(ids));
    }

    /**
     * 启用或停用渠道
     */
    @Operation(summary = "启用或停用渠道", description = "停用后该渠道不参与路由与降级")
    @PreAuthorize("@ss.hasPermi('ai:channel:edit')")
    @Log(title = "AI 渠道", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@Parameter(description = "渠道ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "0停用 1启用", required = true) @RequestParam Integer enabled)
    {
        return toAjax(aiChannelService.updateStatus(id, enabled));
    }

    /**
     * 测试连通性
     */
    @Operation(summary = "测试连通性", description = "发一个最小请求，并回写渠道健康状态")
    @PreAuthorize("@ss.hasPermi('ai:channel:test')")
    @Log(title = "AI 渠道", businessType = BusinessType.OTHER)
    @PostMapping("/{id}/test")
    public AjaxResult test(@Parameter(description = "渠道ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "指定模型ID，留空取该渠道第一个启用的模型")
            @RequestParam(required = false) Long modelId)
    {
        AiHealthResult result = aiChannelService.testConnection(id, modelId);
        return result.success() ? success(result.message()) : error(result.message());
    }
}
