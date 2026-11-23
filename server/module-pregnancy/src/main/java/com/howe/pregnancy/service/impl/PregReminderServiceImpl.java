package com.howe.pregnancy.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregTask;
import com.howe.pregnancy.domain.vo.TaskVO;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提醒规则服务实现
 *
 * <p>实现默认值：产检提前 1 天 20:00、当日 07:30；每日记录 21:00 默认关闭；
 * 待产包每周日 10:00 默认关闭。同一产检同一时刻合并为一条消息。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class PregReminderServiceImpl implements IPregReminderService {

    private static final int DEFAULT_VERSION = 1;

    private final PregTaskMapper taskMapper;

    private final IPregTaskService taskService;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public Map<String, Object> getRules() {
        accessGuard.requireManager();
        JSONObject stored = PregnancyReminderRule.readSettings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reminderVersion", stored.getInteger("reminderVersion") == null
                ? DEFAULT_VERSION : stored.getInteger("reminderVersion"));
        result.put("rules", defaultRules(stored));
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
        JSONObject stored = PregnancyReminderRule.readSettings();
        int current = stored.getInteger("reminderVersion") == null
                ? DEFAULT_VERSION : stored.getInteger("reminderVersion");
        if (body.reminderVersion() != current) {
            throw PregnancyException.versionConflict("提醒规则已被修改，请刷新后重试");
        }
        Map<String, JSONObject> rules = new LinkedHashMap<>();
        JSONArray previous = stored.getJSONArray("rules");
        if (previous != null) {
            for (int i = 0; i < previous.size(); i++) {
                JSONObject item = previous.getJSONObject(i);
                if (item != null && item.getString("code") != null) rules.put(item.getString("code"), item);
            }
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
                String time = item.time() == null || item.time().isBlank() ? definition.getDefaultTime() : item.time();
                if (!PregnancyReminderRule.validTime(time)) {
                    throw PregnancyException.invalid("提醒时刻格式应为 HH:mm");
                }
                JSONObject rule = new JSONObject();
                rule.put("code", item.code());
                rule.put("enabled", Boolean.TRUE.equals(item.enabled()));
                rule.put("time", time);
                rule.put("daysBefore", item.daysBefore());
                rule.put("weekday", item.weekday());
                rules.put(item.code(), rule);
            }
        }
        JSONObject next = new JSONObject();
        next.put("reminderVersion", current + 1);
        next.put("rules", new ArrayList<>(rules.values()));
        saveStored(next);
        // 规则变化使尚未发送的旧任务失效
        Map<String, Object> result = getRules();
        result.put("channel", channelState());
        return result;
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
     * 保存规则
     *
     * @param value 规则对象
     */
    private void saveStored(JSONObject value) {
        // 通过系统配置服务写入，保持缓存刷新与敏感参数策略一致
        com.howe.system.service.ISysConfigService configService =
                com.howe.common.utils.spring.SpringUtils.getBean(com.howe.system.service.ISysConfigService.class);
        // updateConfig 需要主键，先按 key 查出参数行
        com.howe.system.domain.SysConfig query = new com.howe.system.domain.SysConfig();
        query.setConfigKey(PregnancyConstants.CONFIG_REMINDER_RULES);
        com.howe.system.domain.SysConfig existing =
                com.howe.common.utils.spring.SpringUtils.getBean(com.howe.system.mapper.SysConfigMapper.class)
                        .selectConfig(query);
        if (existing == null) {
            throw PregnancyException.invalid("提醒规则参数不存在，请先执行孕期参数初始化");
        }
        com.howe.system.domain.SysConfig config = new com.howe.system.domain.SysConfig();
        config.setConfigId(existing.getConfigId());
        config.setConfigKey(PregnancyConstants.CONFIG_REMINDER_RULES);
        config.setConfigValue(value.toJSONString());
        config.setConfigName("孕期-提醒规则");
        config.setConfigType("N");
        config.setRemark("受控 JSON：规则列表与 reminderVersion");
        config.setUpdateBy(com.howe.common.utils.SecurityUtils.getUsername());
        int updated = configService.updateConfig(config);
        if (updated == 0) {
            throw PregnancyException.invalid("提醒规则参数不存在，请先执行孕期参数初始化");
        }
    }

    /** 合并默认值与各账号已保存的通知设置。 */
    private List<Map<String, Object>> defaultRules(JSONObject stored) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PregnancyReminderRule rule : PregnancyReminderRule.values()) {
            PregnancyReminderRule.Setting setting = rule.resolve(stored);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", rule.name());
            view.put("label", rule.getLabel());
            view.put("enabled", setting.enabled());
            view.put("time", setting.time().toString());
            view.put("daysBefore", setting.daysBefore());
            view.put("weekday", setting.weekday());
            result.add(view);
        }
        return result;
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
