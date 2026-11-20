package com.howe.pregnancy.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.dto.ExpenseSaveBody;
import com.howe.pregnancy.domain.vo.ExpenseVO;
import com.howe.pregnancy.service.IPregExpenseService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 孕期费用 控制层
 *
 * <p>金额只在来源详情录入：产检与报告费用挂在各自来源下，待产包金额由物品 PATCH 维护；
 * 花费页只读取统一明细与后端统计。</p>
 *
 * @author howe
 */
@Tag(name = "孕期-费用", description = "统一费用明细与统计")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregExpenseController extends BaseController {

    private final IPregExpenseService expenseService;

    private final PregnancyAccessGuard accessGuard;

    /**
     * 分页查询费用
     *
     * @param from       起始支出日
     * @param to         结束支出日
     * @param category   分类
     * @param sourceType 来源类型
     * @return 分页结果
     */
    @Operation(summary = "查询费用", description = "按支出日期、分类与来源类型过滤")
    @GetMapping("/expenses")
    public TableDataInfo list(
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to,
            @Parameter(description = "分类") @RequestParam(value = "category", required = false) String category,
            @Parameter(description = "来源类型") @RequestParam(value = "sourceType", required = false) String sourceType) {
        startPage();
        List<ExpenseVO> list = expenseService.list(parseDate(from), parseDate(to), category, sourceType);
        return getDataTable(list);
    }

    /**
     * 查询费用统计
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 统计
     */
    @Operation(summary = "查询费用统计", description = "按分类与来源汇总，未填写金额不参与")
    @GetMapping("/expense-statistics")
    public AjaxResult statistics(
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to) {
        Map<String, Object> result = expenseService.statistics(parseDate(from), parseDate(to));
        return success(result);
    }

    /**
     * 新增产检费用
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 新建费用
     */
    @Operation(summary = "新增产检费用", description = "校验父版本并递增产检版本")
    @Log(title = "孕期费用", businessType = BusinessType.INSERT)
    @PostMapping("/checkups/{id}/expenses")
    public AjaxResult createCheckupExpense(@PathVariable("id") Long id,
                                           @Valid @RequestBody ExpenseSaveBody body) {
        return success(expenseService.create(PregnancyConstants.EXPENSE_SOURCE_CHECKUP, id, body));
    }

    /**
     * 新增报告费用
     *
     * @param id   报告 ID
     * @param body 请求
     * @return 新建费用
     */
    @Operation(summary = "新增报告费用", description = "报告与产检关联时同一笔费用只统计一次")
    @Log(title = "孕期费用", businessType = BusinessType.INSERT)
    @PostMapping("/reports/{id}/expenses")
    public AjaxResult createReportExpense(@PathVariable("id") Long id,
                                          @Valid @RequestBody ExpenseSaveBody body) {
        return success(expenseService.create(PregnancyConstants.EXPENSE_SOURCE_REPORT, id, body));
    }

    /**
     * 编辑产检费用
     *
     * @param id        产检 ID
     * @param expenseId 费用 ID
     * @param body      请求
     * @return 更新后的费用
     */
    @Operation(summary = "编辑产检费用", description = "父版本与费用版本同时校验")
    @RequestMapping(path = "/checkups/{id}/expenses/{expenseId}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public AjaxResult updateCheckupExpense(@PathVariable("id") Long id,
                                           @PathVariable("expenseId") Long expenseId,
                                           @Valid @RequestBody ExpenseSaveBody body) {
        return success(expenseService.update(PregnancyConstants.EXPENSE_SOURCE_CHECKUP, id, expenseId, body));
    }

    /**
     * 编辑报告费用
     *
     * @param id        报告 ID
     * @param expenseId 费用 ID
     * @param body      请求
     * @return 更新后的费用
     */
    @Operation(summary = "编辑报告费用", description = "父版本与费用版本同时校验")
    @RequestMapping(path = "/reports/{id}/expenses/{expenseId}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public AjaxResult updateReportExpense(@PathVariable("id") Long id,
                                          @PathVariable("expenseId") Long expenseId,
                                          @Valid @RequestBody ExpenseSaveBody body) {
        return success(expenseService.update(PregnancyConstants.EXPENSE_SOURCE_REPORT, id, expenseId, body));
    }

    /**
     * 删除产检费用
     *
     * @param id        产检 ID
     * @param expenseId 费用 ID
     * @param version   费用版本
     * @return 操作结果
     */
    @Operation(summary = "删除产检费用", description = "逻辑删除")
    @DeleteMapping("/checkups/{id}/expenses/{expenseId}")
    public AjaxResult deleteCheckupExpense(@PathVariable("id") Long id,
                                           @PathVariable("expenseId") Long expenseId,
                                           @RequestParam("version") Integer version) {
        expenseService.delete(PregnancyConstants.EXPENSE_SOURCE_CHECKUP, id, expenseId, version);
        return success();
    }

    /**
     * 删除报告费用
     *
     * @param id        报告 ID
     * @param expenseId 费用 ID
     * @param version   费用版本
     * @return 操作结果
     */
    @Operation(summary = "删除报告费用", description = "逻辑删除")
    @DeleteMapping("/reports/{id}/expenses/{expenseId}")
    public AjaxResult deleteReportExpense(@PathVariable("id") Long id,
                                          @PathVariable("expenseId") Long expenseId,
                                          @RequestParam("version") Integer version) {
        expenseService.delete(PregnancyConstants.EXPENSE_SOURCE_REPORT, id, expenseId, version);
        return success();
    }

    /**
     * 解析可选日期
     *
     * @param text 文本
     * @return 日期
     */
    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return PregnancyTimeUtils.parseDate(text, "日期");
    }
}
