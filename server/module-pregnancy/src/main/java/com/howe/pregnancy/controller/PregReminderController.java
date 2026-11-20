package com.howe.pregnancy.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.service.IPregReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 提醒规则 控制层
 *
 * <p>规则由孕期管理员维护；凭证与通道总开关继续在系统参数设置中管理，
 * 本接口不返回凭证明文。</p>
 *
 * @author howe
 */
@Tag(name = "孕期-提醒规则", description = "规则、版本、通道状态与失败重试")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregReminderController extends BaseController {

    private final IPregReminderService reminderService;

    /**
     * 查询提醒规则
     *
     * @return 规则与通道状态
     */
    @Operation(summary = "查询提醒规则", description = "含 reminderVersion、通道 configured/enabled 与最近一次发送摘要")
    @GetMapping("/reminder-rules")
    public AjaxResult getRules() {
        return success(reminderService.getRules());
    }

    /**
     * 更新提醒规则
     *
     * @param body 请求
     * @return 更新后的规则
     */
    @Operation(summary = "更新提醒规则", description = "比较 reminderVersion 后整体更新")
    @Log(title = "孕期提醒规则", businessType = BusinessType.UPDATE)
    @PutMapping("/reminder-rules")
    public AjaxResult updateRules(@Valid @RequestBody UpdateRulesBody body) {
        List<IPregReminderService.RuleItem> items = body.getRules() == null ? List.of()
                : body.getRules().stream().map(item -> new IPregReminderService.RuleItem(
                        item.getCode(), item.getEnabled(), item.getTime(),
                        item.getDaysBefore(), item.getWeekday())).toList();
        return success(reminderService.updateRules(
                new IPregReminderService.ReminderRulesBody(body.getReminderVersion(), items)));
    }

    /**
     * 重试失败的提醒
     *
     * @param taskId 任务 ID
     * @return 任务摘要
     */
    @Operation(summary = "重试提醒", description = "仅限有效窗口内明确失败的提醒；UNKNOWN 不可重试")
    @PostMapping("/reminder-attempts/{taskId}/retry")
    public AjaxResult retry(@PathVariable("taskId") Long taskId) {
        return success(reminderService.retryAttempt(taskId));
    }

    /**
     * 规则更新请求
     */
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "提醒规则更新请求")
    public static class UpdateRulesBody {

        @NotNull(message = "提醒规则版本不能为空")
        private Integer reminderVersion;

        private List<Rule> rules;
    }

    /**
     * 单条规则
     */
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "提醒规则")
    public static class Rule {

        private String code;

        private Boolean enabled;

        private String time;

        private Integer daysBefore;

        private Integer weekday;
    }
}
