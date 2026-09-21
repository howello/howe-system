package com.howe.ai.controller;

import com.howe.ai.persistence.domain.AiSceneRoute;
import com.howe.ai.service.IAiSceneRouteService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 场景路由 控制层
 *
 * <p>改这里的配置就换了厂商，保存后缓存立即刷新，不需要重启。</p>
 *
 * @author howe
 */
@Tag(name = "AI 场景路由", description = "场景到模型的映射，含有序降级链与参数覆盖")
@RestController
@RequestMapping("/ai/route")
@RequiredArgsConstructor
public class AiSceneRouteController extends BaseController
{
    private final IAiSceneRouteService aiSceneRouteService;

    /**
     * 查询场景路由列表
     */
    @Operation(summary = "查询场景路由列表", description = "分页查询，联表带出主模型名称")
    @PreAuthorize("@ss.hasPermi('ai:route:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiSceneRoute aiSceneRoute)
    {
        startPage();
        List<AiSceneRoute> list = aiSceneRouteService.selectAiSceneRouteList(aiSceneRoute);
        return getDataTable(list);
    }

    /**
     * 新增或修改场景路由
     */
    @Operation(summary = "新增或修改场景路由",
            description = "带 id 时按 id 更新；不带 id 时按「场景 + 能力」判断是新增还是覆盖")
    @PreAuthorize("@ss.hasPermi('ai:route:edit')")
    @Log(title = "AI 场景路由", businessType = BusinessType.UPDATE)
    @PostMapping
    public AjaxResult save(@Valid @RequestBody AiSceneRoute aiSceneRoute)
    {
        return toAjax(aiSceneRouteService.saveAiSceneRoute(aiSceneRoute));
    }

    /**
     * 删除场景路由
     */
    @Operation(summary = "删除场景路由")
    @PreAuthorize("@ss.hasPermi('ai:route:remove')")
    @Log(title = "AI 场景路由", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@Parameter(description = "路由ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] ids)
    {
        return toAjax(aiSceneRouteService.deleteAiSceneRouteByIds(ids));
    }
}
