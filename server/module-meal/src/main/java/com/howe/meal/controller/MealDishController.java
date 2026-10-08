package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealDish;
import com.howe.meal.service.IMealDishService;
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
 * 点餐-菜品 控制层
 *
 * <p>列表与详情不加权限注解（点餐端与管理端共用，登录即可）；
 * 增删改由 {@code meal:dish:*} 控制。菜品详情会校验家庭归属，
 * 跨家庭读别人家的私有菜会被拒绝。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-菜品", description = "公共菜谱（dept_id=0）与家庭私有菜")
@RestController
@RequestMapping("/meal/dish")
@RequiredArgsConstructor
public class MealDishController extends BaseController {

    private final IMealDishService mealDishService;

    @Operation(summary = "查询菜品列表", description = "支持按分类、状态、菜名、关键词（菜名或用料）过滤")
    @GetMapping("/list")
    public TableDataInfo list(MealDish mealDish) {
        startPage();
        List<MealDish> list = mealDishService.selectMealDishList(mealDish);
        return getDataTable(list);
    }

    @Operation(summary = "获取菜品详情", description = "含用料、做法与小贴士；跨家庭读取私有菜被拒绝")
    @GetMapping(value = "/{dishId}")
    public AjaxResult getInfo(@Parameter(description = "菜品ID", required = true)
            @PathVariable("dishId") Long dishId) {
        return success(mealDishService.selectMealDishById(dishId));
    }

    @Operation(summary = "新增菜品")
    @PreAuthorize("@ss.hasPermi('meal:dish:add')")
    @Log(title = "点餐菜品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealDish mealDish) {
        return toAjax(mealDishService.insertMealDish(mealDish));
    }

    @Operation(summary = "修改菜品")
    @PreAuthorize("@ss.hasPermi('meal:dish:edit')")
    @Log(title = "点餐菜品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MealDish mealDish) {
        return toAjax(mealDishService.updateMealDish(mealDish));
    }

    @Operation(summary = "删除菜品")
    @PreAuthorize("@ss.hasPermi('meal:dish:remove')")
    @Log(title = "点餐菜品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dishIds}")
    public AjaxResult remove(@Parameter(description = "菜品ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] dishIds) {
        return toAjax(mealDishService.deleteMealDishByIds(dishIds));
    }
}
