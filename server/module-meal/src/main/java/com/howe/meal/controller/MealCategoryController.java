package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.dto.MealCategorySaveRequest;
import com.howe.meal.domain.dto.MealCategorySaveResult;
import com.howe.meal.service.IMealCategoryService;
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
 * 点餐-菜品分类 控制层
 *
 * <p>列表与详情不加权限注解：点餐端和管理端都要用，只要登录就能读。
 * 增删改由 {@code meal:category:*} 控制，归属与公共分类限制在 Service 层再次校验。</p>
 *
 * <p>新增与修改会先经过 AI 分类审阅场景：通过时返回润色后的保存值，
 * 不通过时返回结构化原因与修改示例（HTTP 200 + 业务结构，不抛业务异常）。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-菜品分类", description = "公共分类（dept_id=0，固定 8 个）与家庭私有分类")
@RestController
@RequestMapping("/meal/category")
@RequiredArgsConstructor
public class MealCategoryController extends BaseController {

    private final IMealCategoryService mealCategoryService;

    @Operation(summary = "查询分类列表", description = "返回公共分类与当前家庭的私有分类；家庭账号强制只看本家庭")
    @GetMapping("/list")
    public TableDataInfo list(MealCategory mealCategory) {
        startPage();
        List<MealCategory> list = mealCategoryService.selectMealCategoryList(mealCategory);
        return getDataTable(list);
    }

    @Operation(summary = "获取分类详情")
    @GetMapping(value = "/{categoryId}")
    public AjaxResult getInfo(@Parameter(description = "分类ID", required = true)
            @PathVariable("categoryId") Long categoryId) {
        return success(mealCategoryService.selectMealCategoryById(categoryId));
    }

    @Operation(summary = "新增分类", description = "仅家庭私有分类可新增；公共分类名称与数量锁定。"
            + "保存前经 AI 审阅：通过则保存润色值，不通过返回原因与修改示例，AI 不可用时降级保存并标记 SKIPPED")
    @PreAuthorize("@ss.hasPermi('meal:category:add')")
    @Log(title = "点餐分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealCategorySaveRequest request) {
        MealCategorySaveResult result = mealCategoryService.insertMealCategory(request);
        return success(result);
    }

    @Operation(summary = "修改分类", description = "公共分类仅平台管理员可改说明、依据、图标、排序、状态与备注，名称不可改；"
            + "家庭私有分类可由所属家庭管理员与超管修改。保存前经 AI 审阅")
    @PreAuthorize("@ss.hasPermi('meal:category:edit')")
    @Log(title = "点餐分类", businessType = BusinessType.UPDATE)
    @PutMapping("/{categoryId}")
    public AjaxResult edit(@Parameter(description = "分类ID", required = true)
            @PathVariable("categoryId") Long categoryId,
            @Valid @RequestBody MealCategorySaveRequest request) {
        MealCategorySaveResult result = mealCategoryService.updateMealCategory(categoryId, request);
        return success(result);
    }

    @Operation(summary = "删除分类", description = "公共分类不可删除；分类下仍有菜品或提案时整批拒绝")
    @PreAuthorize("@ss.hasPermi('meal:category:remove')")
    @Log(title = "点餐分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public AjaxResult remove(@Parameter(description = "分类ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] categoryIds) {
        return toAjax(mealCategoryService.deleteMealCategoryByIds(categoryIds));
    }
}
