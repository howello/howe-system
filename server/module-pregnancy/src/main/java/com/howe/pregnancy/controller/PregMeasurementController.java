package com.howe.pregnancy.controller;

import com.github.pagehelper.PageInfo;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.domain.dto.MeasurementSaveBody;
import com.howe.pregnancy.domain.vo.MeasurementVO;
import com.howe.pregnancy.service.IPregMeasurementService;
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
import java.util.List;
import java.util.Map;

/**
 * 固定指标与测量 控制层
 *
 * <p>指标目录由代码提供，只读；新增走 {@code requestId} 幂等，编辑与删除走 {@code version}。
 * 分页沿用 {@code TableDataInfo} 形状。</p>
 *
 * @author howe
 */
@Tag(name = "孕期-指标与测量", description = "固定指标目录、测量记录与统计")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregMeasurementController extends BaseController {

    private final IPregMeasurementService measurementService;

    /**
     * 查询指标目录
     *
     * @return 指标列表
     */
    @Operation(summary = "查询固定指标目录", description = "返回编码、名称、字段、单位、精度、场景、选项与默认展示顺序")
    @GetMapping("/metrics")
    public AjaxResult metrics() {
        List<Map<String, Object>> list = com.howe.pregnancy.util.BuiltinMetricCatalog.all().stream()
                .map(PregMeasurementController::toMetricView).toList();
        return success(list);
    }

    /**
     * 分页查询测量记录
     *
     * @param metricCode 指标编码
     * @param from       起始日期
     * @param to         结束日期
     * @param scene      场景
     * @param unit       单位
     * @param reportId   报告 ID
     * @return 分页结果
     */
    @Operation(summary = "查询测量记录", description = "按发生时刻倒序，可按指标、日期、场景、单位与报告过滤")
    @GetMapping("/measurements")
    public TableDataInfo list(
            @Parameter(description = "指标编码") @RequestParam(value = "metricCode", required = false) String metricCode,
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to,
            @Parameter(description = "场景编码") @RequestParam(value = "scene", required = false) String scene,
            @Parameter(description = "单位") @RequestParam(value = "unit", required = false) String unit,
            @Parameter(description = "报告ID") @RequestParam(value = "reportId", required = false) String reportId) {
        PageInfo<MeasurementVO> page = measurementService.list(metricCode,
                parseOptionalDate(from, "起始日期"), parseOptionalDate(to, "结束日期"),
                scene, unit, parseOptionalLong(reportId, "报告ID"));
        TableDataInfo result = getDataTable(page.getList());
        result.setTotal(page.getTotal());
        return result;
    }

    /**
     * 查询测量记录详情
     *
     * @param id 记录 ID
     * @return 记录详情
     */
    @Operation(summary = "查询测量记录详情", description = "返回指标编码、值、单位、场景、归因与版本")
    @GetMapping("/measurements/{id}")
    public AjaxResult detail(@PathVariable("id") Long id) {
        return success(measurementService.detail(id));
    }

    /**
     * 新增测量记录
     *
     * @param body 请求
     * @return 新建记录
     */
    @Operation(summary = "新增测量记录", description = "固定指标校验；同 requestId 同内容返回原记录，不同内容返回幂等冲突")
    @Log(title = "孕期测量", businessType = BusinessType.INSERT)
    @PostMapping("/measurements")
    public AjaxResult create(@Valid @RequestBody MeasurementSaveBody body) {
        return success(measurementService.create(body));
    }

    /**
     * 编辑测量记录
     *
     * @param id   记录 ID
     * @param body 请求
     * @return 更新后的记录
     */
    @Operation(summary = "编辑测量记录", description = "指标编码不变，按记录版本比较；未出现字段保持原值")
    @Log(title = "孕期测量", businessType = BusinessType.UPDATE)
    @PutMapping("/measurements/{id}")
    public AjaxResult update(@PathVariable("id") Long id,
                             @Valid @RequestBody com.howe.pregnancy.domain.dto.MeasurementUpdateBody body) {
        return success(measurementService.update(id, body));
    }

    /**
     * 逻辑删除测量记录
     *
     * @param id      记录 ID
     * @param version 记录版本
     * @return 操作结果
     */
    @Operation(summary = "删除测量记录", description = "逻辑删除，从查询与统计排除，保留审计")
    @Log(title = "孕期测量", businessType = BusinessType.DELETE)
    @DeleteMapping("/measurements/{id}")
    public AjaxResult remove(@PathVariable("id") Long id,
                             @RequestParam("version") Integer version) {
        measurementService.delete(id, version);
        return success();
    }

    /**
     * 查询指标统计
     *
     * @param metricCode  指标编码
     * @param from        起始日期
     * @param to          结束日期
     * @param scene       场景
     * @param unit        单位
     * @param aggregation 聚合口径
     * @return 统计结果
     */
    @Operation(summary = "查询指标统计", description = "按单位与场景分别成组；DAY_SUM 仅开放给允许求和的指标")
    @GetMapping("/metrics/{metricCode}/statistics")
    public AjaxResult statistics(
            @PathVariable("metricCode") String metricCode,
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to,
            @Parameter(description = "场景编码") @RequestParam(value = "scene", required = false) String scene,
            @Parameter(description = "单位") @RequestParam(value = "unit", required = false) String unit,
            @Parameter(description = "聚合口径（RAW/DAY_LAST/DAY_SUM）")
            @RequestParam(value = "aggregation", required = false) String aggregation) {
        return success(measurementService.statistics(metricCode,
                parseOptionalDate(from, "起始日期"), parseOptionalDate(to, "结束日期"),
                scene, unit, aggregation));
    }

    /**
     * 把指标定义转成展示结构
     *
     * @param metric 指标定义
     * @return 展示结构
     */
    private static Map<String, Object> toMetricView(com.howe.pregnancy.domain.FixedMetric metric) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("metricCode", metric.code());
        view.put("name", metric.name());
        view.put("kind", metric.kind());
        view.put("sortOrder", metric.sortOrder());
        view.put("visible", metric.visible());
        view.put("units", metric.units());
        view.put("secondaryValue", metric.secondaryValue());
        view.put("summable", metric.summable());
        view.put("sceneRequired", metric.sceneRequired());
        List<Map<String, Object>> fields = metric.fields().stream().map(field -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("code", field.code());
            item.put("label", field.label());
            item.put("type", field.type());
            item.put("scale", field.scale());
            item.put("required", field.required());
            if (field.options() != null) {
                item.put("options", field.options().stream().map(option -> Map.of(
                        "code", option.code(), "label", option.label())).toList());
            }
            return item;
        }).toList();
        view.put("fields", fields);
        view.put("scenes", metric.scenes().stream().map(scene -> Map.of(
                "code", scene.code(), "label", scene.label())).toList());
        return view;
    }

    /**
     * 解析可选日期
     *
     * @param text  文本
     * @param field 字段名
     * @return 日期，空文本返回 null
     */
    private static LocalDate parseOptionalDate(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return PregnancyTimeUtils.parseDate(text, field);
    }

    /**
     * 解析可选 ID
     *
     * @param text  文本
     * @param field 字段名
     * @return ID，空文本返回 null
     */
    private static Long parseOptionalLong(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw com.howe.pregnancy.util.PregnancyException.invalid(field + "格式不正确");
        }
    }
}
