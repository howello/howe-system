package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.common.enums.LimitType;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.dto.DishAiGenerateRequest;
import com.howe.meal.domain.dto.DishAiUpdateRequest;
import com.howe.meal.domain.dto.DishOwnerChangeRequest;
import com.howe.meal.service.MealDishSearchService;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.service.MealDishAiService;
import com.howe.meal.service.MealDishAiTaskService;
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

    private final MealDishAiService mealDishAiService;

    private final MealDishAiTaskService aiTaskService;

    private final MealDishSearchService mealDishSearchService;

    @Operation(summary = "查询菜品列表", description = "支持按分类、状态、菜名、关键词（菜名或用料）过滤")
    @GetMapping("/list")
    public TableDataInfo list(MealDish mealDish) {
        startPage();
        List<MealDish> list = mealDishService.selectMealDishList(mealDish);
        return getDataTable(list);
    }

    /**
     * 搜索当前用户可见的菜品；仅在菜名本地匹配无结果时由服务端调用 AI 语义扩展。
     *
     * @param keyword 菜名或简短描述
     * @return 当前可见菜品候选
     */
    @Operation(summary = "搜索可见菜品", description = "先匹配当前菜单中的菜名；无匹配时调用 AI 理解别名与同义词，结果仅来自当前用户可见的上架菜品")
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @GetMapping("/search")
    public AjaxResult search(@Parameter(description = "菜名或简短描述", required = true)
                             @RequestParam("keyword") String keyword) {
        return success(mealDishSearchService.search(keyword));
    }

    @Operation(summary = "获取菜品详情", description = "含用料、做法与小贴士；跨家庭读取私有菜被拒绝")
    @GetMapping(value = "/{dishId}")
    public AjaxResult getInfo(@Parameter(description = "菜品ID", required = true)
            @PathVariable("dishId") Long dishId) {
        return success(mealDishService.selectMealDishById(dishId));
    }

    @Operation(summary = "一键 AI 补齐菜品文本信息",
            description = "根据菜名只补齐当前为空的文本字段，不覆盖已填；分类只从现有分类中匹配。登录即可调用，点餐端与后台共用。")
    @PostMapping("/ai-generate/text")
    public AjaxResult aiGenerateText(@RequestBody DishAiGenerateRequest request) {
        return success(mealDishAiService.generateText(request));
    }

    @Operation(summary = "一键 AI 生成菜品封面",
            description = "根据菜名生成封面图并由 AI 模块转存为永久地址；普通补全时已填封面不生成，"
                    + "编辑页传 forceRegenerate=true 与 imageDescription 时按描述强制重建。登录即可调用，点餐端与后台共用。")
    @PostMapping("/ai-generate/cover")
    public AjaxResult aiGenerateCover(@RequestBody DishAiGenerateRequest request) {
        return success(mealDishAiService.generateCover(request));
    }

    @Operation(summary = "AI 更新菜品信息（异步）",
            description = "从用户输入的完整做法中提取简介、标签、耗时、难度、份量、热量、用料、做法与小贴士，"
                    + "非空字段覆盖入库；未提取到的字段保留原值，菜名、封面、分类与归属不变。"
                    + "接口校验权限后立即返回，提取与落库在服务端线程池异步执行。需要 meal:dish:edit 权限。")
    @PreAuthorize("@ss.hasPermi('meal:dish:edit')")
    @Log(title = "点餐菜品", businessType = BusinessType.UPDATE)
    @PostMapping("/ai-update")
    public AjaxResult aiUpdate(@RequestBody DishAiUpdateRequest request) {
        return success(java.util.Map.of("taskId", mealDishAiService.submitUpdateFromText(request)));
    }

    /** 查询当前用户提交的菜品 AI 更新任务结果。 */
    @Operation(summary = "查询菜品 AI 更新状态")
    @PreAuthorize("@ss.hasPermi('meal:dish:edit')")
    @GetMapping("/ai-update/task/{taskId}")
    public AjaxResult aiUpdateTask(@PathVariable("taskId") String taskId) {
        return success(aiTaskService.get(taskId));
    }

    @Operation(summary = "招牌推荐位", description = "本家庭近 90 天已完成订单中按份数取前 3 道仍上架的菜；"
            + "招牌不是分类、不落库、不可写入。登录即可调用，点餐端使用。")
    @GetMapping("/signature")
    public AjaxResult signature() {
        return success(mealDishService.selectSignatureDishes());
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

    /**
     * 查询可调整归属的家庭
     *
     * <p>只返回有效家庭（F 类型、未删除、启用），不含组织部门。</p>
     */
    @Operation(summary = "查询归属可选家庭", description = "返回有效家庭；权限 meal:dish:owner")
    @PreAuthorize("@ss.hasPermi('meal:dish:owner')")
    @GetMapping("/ownerOptions")
    public AjaxResult ownerOptions(@Parameter(description = "家庭名称模糊搜索词")
            @RequestParam(required = false) String keyword) {
        return success(mealDishService.selectOwnerOptions(keyword));
    }

    /**
     * 调整菜品归属
     *
     * <p>只允许平台管理员在「公共菜谱」与有效家庭之间迁移。带旧归属做并发校验。</p>
     */
    @Operation(summary = "调整菜品归属", description = "仅平台管理员；目标为 0（公共）或有效家庭 ID")
    @PreAuthorize("@ss.hasPermi('meal:dish:owner') and @ss.isAdmin()")
    @Log(title = "点餐菜品归属", businessType = BusinessType.UPDATE)
    @PutMapping("/{dishId}/owner")
    public AjaxResult changeOwner(@Parameter(description = "菜品ID", required = true) @PathVariable("dishId") Long dishId,
            @Valid @RequestBody DishOwnerChangeRequest request) {
        return toAjax(mealDishService.changeDishOwner(dishId, request));
    }
}
