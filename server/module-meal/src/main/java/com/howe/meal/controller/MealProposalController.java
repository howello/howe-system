package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.dto.MealProposalAuditBody;
import com.howe.meal.service.IMealProposalService;
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

/**
 * 点餐-新菜提案 控制层
 *
 * @author howe
 */
@Tag(name = "点餐-新菜提案", description = "家庭成员提交想吃的新菜，管理员审核通过后自动上架")
@RestController
@RequestMapping("/meal/proposal")
@RequiredArgsConstructor
public class MealProposalController extends BaseController {

    private final IMealProposalService mealProposalService;

    @Operation(summary = "提交新菜提案")
    @Log(title = "点餐提案", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealProposal mealProposal) {
        return toAjax(mealProposalService.submitProposal(mealProposal));
    }

    @Operation(summary = "我的提案")
    @GetMapping("/my")
    public TableDataInfo my(MealProposal mealProposal) {
        startPage();
        return getDataTable(mealProposalService.selectMyProposalList(mealProposal));
    }

    @Operation(summary = "查询提案列表（管理端）", description = "受数据权限约束")
    @PreAuthorize("@ss.hasPermi('meal:proposal:list')")
    @GetMapping("/list")
    public TableDataInfo list(MealProposal mealProposal) {
        startPage();
        return getDataTable(mealProposalService.selectMealProposalList(mealProposal));
    }

    @Operation(summary = "审核提案", description = "通过时自动在菜品表生成一条菜品（source=1）并回写菜品ID")
    @PreAuthorize("@ss.hasPermi('meal:proposal:audit')")
    @Log(title = "点餐提案", businessType = BusinessType.UPDATE)
    @PutMapping("/audit")
    public AjaxResult audit(@Valid @RequestBody MealProposalAuditBody body) {
        return toAjax(mealProposalService.auditProposal(body));
    }

    @Operation(summary = "删除提案")
    @PreAuthorize("@ss.hasPermi('meal:proposal:remove')")
    @Log(title = "点餐提案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{proposalIds}")
    public AjaxResult remove(@Parameter(description = "提案ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] proposalIds) {
        return toAjax(mealProposalService.deleteMealProposalByIds(proposalIds));
    }
}
