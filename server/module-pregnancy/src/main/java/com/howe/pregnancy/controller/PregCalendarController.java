package com.howe.pregnancy.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.domain.dto.TodoCreateBody;
import com.howe.pregnancy.domain.dto.TodoUpdateBody;
import com.howe.pregnancy.domain.vo.CalendarDayVO;
import com.howe.pregnancy.domain.vo.CalendarMonthVO;
import com.howe.pregnancy.domain.vo.GrowthVO;
import com.howe.pregnancy.domain.vo.TodoVO;
import com.howe.pregnancy.service.IPregCalendarService;
import com.howe.pregnancy.service.IPregTodoService;
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

/**
 * 日历、待办与每日变化 控制层
 *
 * @author howe
 */
@Tag(name = "孕期-日历与待办", description = "月摘要、当天详情、待办与每日变化时间轴")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregCalendarController extends BaseController {

    private final IPregCalendarService calendarService;

    private final IPregTodoService todoService;

    /**
     * 查询月摘要
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 月摘要
     */
    @Operation(summary = "查询日历月摘要", description = "轻量日期标记与数量，不返回原图或全部明细")
    @GetMapping("/calendar")
    public AjaxResult calendar(@RequestParam("from") String from,
                               @RequestParam("to") String to) {
        CalendarMonthVO vo = calendarService.month(
                PregnancyTimeUtils.parseDate(from, "起始日期"),
                PregnancyTimeUtils.parseDate(to, "结束日期"));
        return success(vo);
    }

    /**
     * 查询当天详情
     *
     * @param date 日期
     * @return 当天详情
     */
    @Operation(summary = "查询日历当天详情", description = "当天记录分组、预约、待办与允许入口")
    @GetMapping("/days/{date}")
    public AjaxResult day(@PathVariable("date") String date) {
        CalendarDayVO vo = calendarService.day(PregnancyTimeUtils.parseDate(date, "日期"));
        return success(vo);
    }

    /**
     * 查询每日变化时间轴
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 时间轴
     */
    @Operation(summary = "查询每日变化时间轴", description = "按日期升序返回每日母婴内容；缺日时回退本周概况")
    @GetMapping("/growth")
    public AjaxResult growth(@RequestParam("from") String from,
                             @RequestParam("to") String to) {
        GrowthVO vo = calendarService.growth(
                PregnancyTimeUtils.parseDate(from, "起始日期"),
                PregnancyTimeUtils.parseDate(to, "结束日期"));
        return success(vo);
    }

    /**
     * 分页查询待办
     *
     * @param done 完成状态
     * @param from 起始日期
     * @param to   结束日期
     * @return 分页结果
     */
    @Operation(summary = "查询待办", description = "按日期与完成状态过滤")
    @GetMapping("/todos")
    public TableDataInfo listTodos(
            @Parameter(description = "是否完成") @RequestParam(value = "done", required = false) Boolean done,
            @Parameter(description = "起始日期") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日期") @RequestParam(value = "to", required = false) String to) {
        startPage();
        List<TodoVO> list = todoService.list(done, parseOptionalDate(from), parseOptionalDate(to));
        return getDataTable(list);
    }

    /**
     * 新增待办
     *
     * @param body 请求
     * @return 新建待办
     */
    @Operation(summary = "新增待办", description = "允许未来日期；同 requestId 同内容返回原记录")
    @Log(title = "孕期待办", businessType = BusinessType.INSERT)
    @PostMapping("/todos")
    public AjaxResult createTodo(@Valid @RequestBody TodoCreateBody body) {
        return success(todoService.create(body));
    }

    /**
     * 编辑待办
     *
     * @param id   待办 ID
     * @param body 请求
     * @return 更新后的待办
     */
    @Operation(summary = "编辑待办", description = "编辑、勾选完成与取消完成；按版本比较")
    @Log(title = "孕期待办", businessType = BusinessType.UPDATE)
    @PutMapping("/todos/{id}")
    public AjaxResult updateTodo(@PathVariable("id") Long id,
                                 @Valid @RequestBody TodoUpdateBody body) {
        return success(todoService.update(id, body));
    }

    /**
     * 逻辑删除待办
     *
     * @param id      待办 ID
     * @param version 版本
     * @return 操作结果
     */
    @Operation(summary = "删除待办", description = "逻辑删除")
    @Log(title = "孕期待办", businessType = BusinessType.DELETE)
    @DeleteMapping("/todos/{id}")
    public AjaxResult removeTodo(@PathVariable("id") Long id,
                                 @RequestParam("version") Integer version) {
        todoService.delete(id, version);
        return success();
    }

    /**
     * 解析可选日期
     *
     * @param text 文本
     * @return 日期
     */
    private static LocalDate parseOptionalDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return PregnancyTimeUtils.parseDate(text, "日期");
    }
}
