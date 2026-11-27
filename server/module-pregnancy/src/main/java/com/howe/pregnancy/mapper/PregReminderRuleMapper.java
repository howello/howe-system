package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregReminderRuleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提醒规则数据层
 *
 * <p>规则表只有一份，读取后与枚举默认值合并；整体保存时用表级版本号做乐观锁。</p>
 *
 * @author howe
 */
public interface PregReminderRuleMapper {

    /**
     * 查询全部规则行
     *
     * @return 规则列表
     */
    List<PregReminderRuleEntity> selectAll();

    /**
     * 按编码查询
     *
     * @param ruleCode 规则编码
     * @return 规则，不存在返回 null
     */
    PregReminderRuleEntity selectByCode(@Param("ruleCode") String ruleCode);

    /**
     * 新增规则
     *
     * @param rule 规则
     * @return 影响行数
     */
    int insert(PregReminderRuleEntity rule);

    /**
     * 按编码更新规则内容（不动版本号）
     *
     * @param rule 规则
     * @return 影响行数
     */
    int updateByCode(PregReminderRuleEntity rule);

    /**
     * 读取当前表级版本号
     *
     * @return 版本号，无规则时返回 null
     */
    Integer selectVersion();

    /**
     * 整体递增表级版本号（保存成功后调用）
     *
     * @param expected 期望的旧版本号
     * @param next     新版本号
     * @return 影响行数，0 表示版本已被他人改动
     */
    int bumpVersion(@Param("expected") Integer expected, @Param("next") Integer next);
}
