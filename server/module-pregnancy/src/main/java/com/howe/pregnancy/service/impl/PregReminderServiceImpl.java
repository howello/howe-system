package com.howe.pregnancy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.utils.ConfigUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregTask;
import com.howe.pregnancy.domain.vo.TaskVO;
import com.howe.pregnancy.mapper.PregTaskMapper;
import com.howe.pregnancy.service.IPregReminderService;
import com.howe.pregnancy.service.IPregTaskService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class PregReminderServiceImpl implements IPregReminderService {

    /** 规则编码：产检提前一天 */
    private static final String RULE_CHECKUP_BEFORE = "CHECKUP_BEFORE";

    /** 规则编码：产检当日 */
    private static final String RULE_CHECKUP_TODAY = "CHECKUP_TODAY";

    /** 规则编码：每日记录 */
    private static final String RULE_DAILY_RECORD = "DAILY_RECORD";

    /** 规则编码：待产包周提醒 */
    private static final String RULE_BAG_WEEKLY = "BAG_WEEKLY";

    private static final int DEFAULT_VERSION = 1;

    private final PregTaskMapper taskMapper;

    private final IPregTaskService taskService;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public Map<String, Object> getRules() {
        accessGuard.requireManager();
        JSONObject stored = readStored();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reminderVersion", stored.getInteger("reminderVersion") == null
                ? DEFAULT_VERSION : stored.getInteger("reminderVersion"));
        result.put("rules", defaultRules(stored.getJSONArray("rules")));
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
        JSONObject stored = readStored();
        int current = stored.getInteger("reminderVersion") == null
                ? DEFAULT_VERSION : stored.getInteger("reminderVersion");
        if (body.reminderVersion() != current) {
            throw PregnancyException.versionConflict("提醒规则已被修改，请刷新后重试");
        }
        JSONArray rules = new JSONArray();
        if (body.rules() != null) {
            for (RuleItem item : body.rules()) {
                if (item == null || item.code() == null || item.code().isBlank()) {
                    throw PregnancyException.invalid("规则编码不能为空");
                }
                if (!List.of(RULE_CHECKUP_BEFORE, RULE_CHECKUP_TODAY, RULE_DAILY_RECORD, RULE_BAG_WEEKLY)
                        .contains(item.code())) {
                    throw PregnancyException.invalid("未知的提醒规则：" + item.code());
                }
                validateTime(item.time());
                JSONObject rule = new JSONObject();
                rule.put("code", item.code());
                rule.put("enabled", Boolean.TRUE.equals(item.enabled()));
                rule.put("time", item.time() == null ? defaultTime(item.code()) : item.time());
                rule.put("daysBefore", item.daysBefore());
                rule.put("weekday", item.weekday());
                rules.add(rule);
            }
        }
        JSONObject next = new JSONObject();
        next.put("reminderVersion", current + 1);
        next.put("rules", rules);
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
        if (task == null || !PregnancyConstants.TASK_REMINDER.equals(task.getKind())) {
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
     * 读取已保存的规则
     *
     * @return 已保存对象，空或损坏时返回空对象
     */
    private JSONObject readStored() {
        String text = ConfigUtils.getString(PregnancyConstants.CONFIG_REMINDER_RULES);
        if (text == null || text.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject object = JSON.parseObject(text);
            return object == null ? new JSONObject() : object;
        } catch (RuntimeException e) {
            log.warn("孕期提醒规则配置不是合法 JSON，按默认值处理");
            return new JSONObject();
        }
    }

    /**
     * 保存规则
     *
     * @param value 规则对象
     */
    private void saveStored(JSONObject value) {
        ConfigUtils.getString(PregnancyConstants.CONFIG_REMINDER_RULES);
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

    /**
     * 合并默认规则
     *
     * @param stored 已保存规则
     * @return 完整规则列表
     */
    private List<Map<String, Object>> defaultRules(JSONArray stored) {
        Map<String, JSONObject> saved = new LinkedHashMap<>();
        if (stored != null) {
            for (int i = 0; i < stored.size(); i++) {
                JSONObject item = stored.getJSONObject(i);
                if (item != null && item.getString("code") != null) {
                    saved.put(item.getString("code"), item);
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(ruleView(saved, RULE_CHECKUP_BEFORE, true, "20:00", 1, null));
        result.add(ruleView(saved, RULE_CHECKUP_TODAY, true, "07:30", 0, null));
        result.add(ruleView(saved, RULE_DAILY_RECORD, false, "21:00", 0, null));
        result.add(ruleView(saved, RULE_BAG_WEEKLY, false, "10:00", 0, 0));
        return result;
    }

    /**
     * 组装单条规则
     *
     * @param saved     已保存规则
     * @param code      编码
     * @param enabled   默认启用
     * @param time      默认时刻
     * @param daysBefore 默认提前天数
     * @param weekday   默认星期
     * @return 规则视图
     */
    private Map<String, Object> ruleView(Map<String, JSONObject> saved, String code, boolean enabled,
                                         String time, Integer daysBefore, Integer weekday) {
        JSONObject item = saved.get(code);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", code);
        view.put("label", labelOf(code));
        view.put("enabled", item == null ? enabled
                : Boolean.TRUE.equals(item.getBoolean("enabled")));
        view.put("time", item == null || item.getString("time") == null ? time : item.getString("time"));
        view.put("daysBefore", item == null || item.getInteger("daysBefore") == null
                ? daysBefore : item.getInteger("daysBefore"));
        view.put("weekday", item == null || item.getInteger("weekday") == null
                ? weekday : item.getInteger("weekday"));
        return view;
    }

    /**
     * 规则显示名
     *
     * @param code 编码
     * @return 显示名
     */
    private String labelOf(String code) {
        return switch (code) {
            case RULE_CHECKUP_BEFORE -> "产检提前提醒";
            case RULE_CHECKUP_TODAY -> "产检当日提醒";
            case RULE_DAILY_RECORD -> "每日记录提醒";
            case RULE_BAG_WEEKLY -> "待产包周提醒";
            default -> code;
        };
    }

    /**
     * 默认时刻
     *
     * @param code 编码
     * @return 时刻
     */
    private String defaultTime(String code) {
        return switch (code) {
            case RULE_CHECKUP_BEFORE -> "20:00";
            case RULE_CHECKUP_TODAY -> "07:30";
            case RULE_DAILY_RECORD -> "21:00";
            case RULE_BAG_WEEKLY -> "10:00";
            default -> "09:00";
        };
    }

    /**
     * 校验时刻格式
     *
     * @param time 时刻
     */
    private void validateTime(String time) {
        if (time == null || time.isBlank()) {
            return;
        }
        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw PregnancyException.invalid("提醒时刻格式应为 HH:mm");
        }
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
