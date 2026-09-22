package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealReview;
import com.howe.meal.service.IMealReviewService;
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

/**
 * 点餐-评价 控制层
 *
 * @author howe
 */
@Tag(name = "点餐-评价", description = "一单一评，只能评价自己已完成的订单")
@RestController
@RequestMapping("/meal/review")
@RequiredArgsConstructor
public class MealReviewController extends BaseController {

    private final IMealReviewService mealReviewService;

    @Operation(summary = "提交评价", description = "限本人已完成且尚未评价的订单，重复提交被拒绝")
    @Log(title = "点餐评价", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealReview mealReview) {
        return toAjax(mealReviewService.submitReview(mealReview));
    }

    @Operation(summary = "我的评价")
    @GetMapping("/my")
    public TableDataInfo my(MealReview mealReview) {
        startPage();
        return getDataTable(mealReviewService.selectMyReviewList(mealReview));
    }

    @Operation(summary = "查询评价列表（管理端）", description = "受数据权限约束")
    @PreAuthorize("@ss.hasPermi('meal:review:list')")
    @GetMapping("/list")
    public TableDataInfo list(MealReview mealReview) {
        startPage();
        return getDataTable(mealReviewService.selectMealReviewList(mealReview));
    }

    @Operation(summary = "删除评价")
    @PreAuthorize("@ss.hasPermi('meal:review:remove')")
    @Log(title = "点餐评价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reviewIds}")
    public AjaxResult remove(@Parameter(description = "评价ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] reviewIds) {
        return toAjax(mealReviewService.deleteMealReviewByIds(reviewIds));
    }
}
