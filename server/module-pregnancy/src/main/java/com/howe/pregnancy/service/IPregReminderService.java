package com.howe.pregnancy.service;

import java.util.Map;

/**
 * 提醒规则服务
 *
 * <p>规则保存在 {@code preg_reminder_rule}（一行一条规则，表级
 * {@code rule_version} 做乐观锁）；由孕期管理员维护，整体更新时比较版本。</p>
 *
 * @author howe
 */
public interface IPregReminderService {

    /**
     * 读取提醒规则与通道状态
     *
     * @return 规则、版本、通道与最近发送摘要
     */
    Map<String, Object> getRules();

    /**
     * 整体更新提醒规则
     *
     * @param body 请求
     * @return 更新后的规则
     */
    Map<String, Object> updateRules(ReminderRulesBody body);

    /**
     * 重试确认失败的提醒
     *
     * @param taskId 任务 ID
     * @return 任务摘要
     */
    com.howe.pregnancy.domain.vo.TaskVO retryAttempt(Long taskId);

    /**
     * 提醒规则请求体
     *
     * @param reminderVersion 版本
     * @param rules           规则列表
     */
    record ReminderRulesBody(Integer reminderVersion, java.util.List<RuleItem> rules) {
    }

    /**
     * 单条规则
     *
     * @param code     规则编码
     * @param enabled  是否启用
     * @param time     默认时刻（HH:mm）
     * @param daysBefore 提前天数
     * @param weekday  星期（待产包周提醒）
     */
    record RuleItem(String code, Boolean enabled, String time, Integer daysBefore, Integer weekday) {
    }
}
