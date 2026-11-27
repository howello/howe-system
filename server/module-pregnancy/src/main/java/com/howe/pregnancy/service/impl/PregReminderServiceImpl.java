package com.howe.pregnancy.service.impl;

import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregReminderRuleEntity;
import com.howe.pregnancy.domain.PregTask;
import com.howe.pregnancy.domain.vo.TaskVO;
import com.howe.pregnancy.mapper.PregReminderRuleMapper;
import com.howe.pregnancy.mapper.PregTaskMapper;
import com.howe.pregnancy.service.IPregReminderService;
import com.howe.pregnancy.service.IPregTaskService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyReminderRule;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提醒规则服务实现
 *
 * <p>规则存放在 {@code preg_reminder_rule}（一行一条），整体保存时以表级
 * {@code rule_version} 做乐观锁。默认值：产检提前 1 天 20:00、当日 07:30；
 * 每日记录 21:00 默认关闭；待产包每周日 10:00 默认关闭。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class PregReminderServiceImpl implements IPregReminderService {

    /** 默认版本号 */
    private static final int DEFAULT_VERSION = 1;

    private final PregTaskMapper taskMapper;

    private final PregReminderRuleMapper ruleMapper;

    private final IPregTaskService taskService;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public Map<String, Object> getRules() {
        accessGuard.requireManager();
        Map<String, PregnancyReminderRule.Setting> stored = PregnancyReminderRule.readSettings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reminderVersion", currentVersion());
        result.put("rules", ruleViews(stored));
        result.put("channel", channelState());
        TaskVO latest = taskService.latestReminder();
        result.put("latest", latest);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateRules(ReminderRulesBody body) {
        accessGuard.requireManager();
        if (body == null || body.reminderVersion() == null) {
            throw PregnancyException.invalid("提醒规则版本不能为空");
        }
        int current = currentVersion();
        if (body.reminderVersion() != current) {
            throw PregnancyException.versionConflict("提醒规则已被修改，请刷新后重试");
        }
        if (body.rules() != null) {
            for (RuleItem item : body.rules()) {
                if (item == null || item.code() == null || item.code().isBlank()) {
                    throw PregnancyException.invalid("规则编码不能为空");
                }
                PregnancyReminderRule definition;
                try {
                    definition = PregnancyReminderRule.valueOf(item.code());
                } catch (IllegalArgumentException e) {
                    throw PregnancyException.invalid("未知的提醒规则：" + item.code());
                }
                String time = item.time() == null || item.time().isBlank()
                        ? definition.getDefaultTime() : item.time();
                if (!PregnancyReminderRule.validTime(time)) {
                    throw PregnancyException.invalid("提醒时刻格式应为 HH:mm");
                }
                PregReminderRuleEntity rule = new PregReminderRuleEntity();
                rule.setRuleCode(item.code());
                rule.setEnabled(Boolean.TRUE.equals(item.enabled()));
                rule.setRemindTime(time);
                rule.setDaysBefore(item.daysBefore());
                rule.setWeekday(item.weekday());
                if (ruleMapper.selectByCode(item.code()) == null) {
                    rule.setRuleVersion(current);
                    ruleMapper.insert(rule);
                } else {
                    ruleMapper.updateByCode(rule);
                }
            }
        }
        // 版本递增失败说明并发修改，回滚本次保存
        if (ruleMapper.bumpVersion(current, current + 1) == 0) {
            throw PregnancyException.versionConflict("提醒规则已被修改，请刷新后重试");
        }
        return getRules();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO retryAttempt(Long taskId) {
        accessGuard.requireManager();
        PregTask task = taskMapper.selectById(taskId);
        if (task == null || !PregnancyConstants.isReminderTask(task.getKind())) {
            throw PregnancyException.notFound();
        }
        if (PregnancyConstants.TASK_UNKNOWN.equals(task.getStatus())) {
            throw PregnancyException.invalidState("结果不明的提醒不能重试");
        }
        if (!PregnancyConstants.TASK_FAILED.equals(task.getStatus())
                && !PregnancyConstants.TASK_RETRY_WAIT.equals(task.getStatus())) {
            throw PregnancyException.invalidState("只有明确失败的提醒可以重试");
        }
        if (task.getNextRunAt() != null
                && task.getNextRunAt().plusMinutes(PregnancyConfig.reminderLateMinutes())
                .isBefore(PregnancyTimeUtils.now())) {
            throw PregnancyException.invalidState("提醒已超过有效窗口，不能重试");
        }
        int attempt = task.getAttempt() == null ? 0 : task.getAttempt();
        taskMapper.extendAttempts(task.getId(), attempt + PregnancyConfig.taskMaxAttempts());
        taskMapper.updateStatus(task.getId(), PregnancyConstants.TASK_PENDING, null,
                PregnancyTimeUtils.now().toLocalDateTime());
        return taskService.getTask(taskId);
    }

    /**
     * 读取当前表级版本号
     *
     * @return 版本号，表中无规则时为默认值
     */
    private int currentVersion() {
        Integer version = ruleMapper.selectVersion();
        return version == null ? DEFAULT_VERSION : version;
    }

    /**
     * 合并默认值与已保存规则，生成视图
     *
     * @param stored 已保存规则
     * @return 规则视图列表
     */
    private List<Map<String, Object>> ruleViews(Map<String, PregnancyReminderRule.Setting> stored) {
        return java.util.Arrays.stream(PregnancyReminderRule.values()).map(rule -> {
            PregnancyReminderRule.Setting setting = rule.resolve(stored);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", rule.name());
            view.put("label", rule.getLabel());
            view.put("enabled", setting.enabled());
            view.put("time", setting.time().toString());
            view.put("daysBefore", setting.daysBefore());
            view.put("weekday", setting.weekday());
            return view;
        }).toList();
    }

    /**
     * 通道状态
     *
     * @return 是否已配置与是否启用
     */
    private Map<String, Object> channelState() {
        String key = PregnancyConfig.wecomKey();
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("configured", key != null && !key.isBlank());
        channel.put("enabled", PregnancyConfig.wecomEnabled());
        return channel;
    }
}
