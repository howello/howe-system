package com.howe.pregnancy.controller;

import com.github.pagehelper.PageInfo;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.domain.dto.DietSaveBody;
import com.howe.pregnancy.domain.dto.ReportSaveBody;
import com.howe.pregnancy.domain.vo.DietVO;
import com.howe.pregnancy.domain.vo.ReportVO;
import com.howe.pregnancy.service.IPregReportService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 报告与饮食 控制层
 *
 * @author howe
 */
@Tag(name = "孕期-报告与饮食", description = "检查报告、多图与按餐次饮食记录")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregReportController extends BaseController {

    private final IPregReportService reportService;

    /**
     * 分页查询报告
     *
     * @param from      起始报告日
     * @param to        结束报告日
     * @param checkupId 产检 ID
     * @return 分页结果
     */
    @Operation(summary = "查询报告", description = "按报告日期与产检过滤，同日多份报告独立返回")
    @GetMapping("/reports")
    public TableDataInfo listReports(
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to,
            @Parameter(description = "产检ID") @RequestParam(value = "checkupId", required = false) String checkupId) {
        PageInfo<ReportVO> page = reportService.listReports(parseDate(from), parseDate(to), parseId(checkupId));
        TableDataInfo result = getDataTable(page.getList());
        result.setTotal(page.getTotal());
        return result;
    }

    /**
     * 查询报告详情
     *
     * @param id 报告 ID
     * @return 报告详情
     */
    @Operation(summary = "查询报告详情", description = "包含有序图片、关联测量与费用")
    @GetMapping("/reports/{id}")
    public AjaxResult getReport(@PathVariable("id") Long id) {
        return success(reportService.getReport(id));
    }

    /**
     * 新增报告
     *
     * @param body 请求
     * @return 新建报告
     */
    @Operation(summary = "新增报告", description = "至少绑定 1 张已上传原图；同 requestId 同内容返回原记录")
    @Log(title = "孕期报告", businessType = BusinessType.INSERT)
    @PostMapping("/reports")
    public AjaxResult createReport(@Valid @RequestBody ReportSaveBody body) {
        return success(reportService.createReport(body));
    }

    /**
     * 编辑报告
     *
     * @param id   报告 ID
     * @param body 请求
     * @return 更新后的报告
     */
    @Operation(summary = "编辑报告", description = "省略 fileIds 不改关联；传列表原子替换")
    @Log(title = "孕期报告", businessType = BusinessType.UPDATE)
    @PutMapping("/reports/{id}")
    public AjaxResult updateReport(@PathVariable("id") Long id,
                                   @Valid @RequestBody com.howe.pregnancy.domain.dto.ReportUpdateBody body) {
        return success(reportService.updateReport(id, body));
    }

    /**
     * 删除报告
     *
     * @param id      报告 ID
     * @param version 版本
     * @return 操作结果
     */
    @Operation(summary = "删除报告", description = "解除测量关联、解绑原图并软删报告费用，保留测量与产检")
    @Log(title = "孕期报告", businessType = BusinessType.DELETE)
    @DeleteMapping("/reports/{id}")
    public AjaxResult removeReport(@PathVariable("id") Long id,
                                   @RequestParam("version") Integer version) {
        reportService.deleteReport(id, version);
        return success();
    }

    /**
     * 分页查询饮食记录
     *
     * @param mealType 餐次
     * @param from     起始日期
     * @param to       结束日期
     * @return 分页结果
     */
    @Operation(summary = "查询饮食记录", description = "按餐次与日期过滤")
    @GetMapping("/diet-records")
    public TableDataInfo listDiets(
            @Parameter(description = "餐次") @RequestParam(value = "mealType", required = false) String mealType,
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to) {
        PageInfo<DietVO> page = reportService.listDiets(mealType, parseDate(from), parseDate(to));
        TableDataInfo result = getDataTable(page.getList());
        result.setTotal(page.getTotal());
        return result;
    }

    /**
     * 查询饮食详情
     *
     * @param id 记录 ID
     * @return 饮食详情
     */
    @Operation(summary = "查询饮食详情", description = "包含照片")
    @GetMapping("/diet-records/{id}")
    public AjaxResult getDiet(@PathVariable("id") Long id) {
        return success(reportService.getDiet(id));
    }

    /**
     * 新增饮食记录
     *
     * @param body 请求
     * @return 新建记录
     */
    @Operation(summary = "新增饮食记录", description = "允许空照片列表；同 requestId 同内容返回原记录")
    @Log(title = "孕期饮食", businessType = BusinessType.INSERT)
    @PostMapping("/diet-records")
    public AjaxResult createDiet(@Valid @RequestBody DietSaveBody body) {
        return success(reportService.createDiet(body));
    }

    /**
     * 编辑饮食记录
     *
     * @param id   记录 ID
     * @param body 请求
     * @return 更新后的记录
     */
    @Operation(summary = "编辑饮食记录", description = "省略 fileIds 不改关联；传列表原子替换")
    @Log(title = "孕期饮食", businessType = BusinessType.UPDATE)
    @PutMapping("/diet-records/{id}")
    public AjaxResult updateDiet(@PathVariable("id") Long id,
                                 @Valid @RequestBody com.howe.pregnancy.domain.dto.DietUpdateBody body) {
        return success(reportService.updateDiet(id, body));
    }

    /**
     * 删除饮食记录
     *
     * @param id      记录 ID
     * @param version 版本
     * @return 操作结果
     */
    @Operation(summary = "删除饮食记录", description = "饮水记录属于指标，不随饮食删除")
    @Log(title = "孕期饮食", businessType = BusinessType.DELETE)
    @DeleteMapping("/diet-records/{id}")
    public AjaxResult removeDiet(@PathVariable("id") Long id,
                                 @RequestParam("version") Integer version) {
        reportService.deleteDiet(id, version);
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

    /**
     * 解析可选 ID
     *
     * @param text 文本
     * @return ID
     */
    private Long parseId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw com.howe.pregnancy.util.PregnancyException.invalid("ID 格式不正确");
        }
    }
}
