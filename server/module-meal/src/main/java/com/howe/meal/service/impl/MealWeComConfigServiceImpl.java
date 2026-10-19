package com.howe.meal.service.impl;

import com.howe.common.constant.HttpStatus;
import com.howe.common.exception.ServiceException;
import com.howe.common.notify.wecom.WeComRobotClient;
import com.howe.common.notify.wecom.WeComRobotResponse;
import com.howe.common.notify.wecom.WeComTextUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealWeComConfig;
import com.howe.meal.mapper.MealWeComConfigMapper;
import com.howe.meal.service.IMealWeComConfigService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 点餐-企业微信通知配置 服务层实现
 *
 * <p>key 是凭证：列表与详情只返回掩码；编辑时传空或掩码表示保留原值。
 * 非平台管理员的新增、修改、删除、测试都限制在本家庭。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealWeComConfigServiceImpl implements IMealWeComConfigService {

    /** 掩码标记 */
    private static final String MASK_MARK = "****";

    private final MealWeComConfigMapper mealWeComConfigMapper;
    private final WeComRobotClient weComRobotClient;

    @Override
    public List<MealWeComConfig> selectMealWeComConfigList(MealWeComConfig config) {
        MealWeComConfig query = config == null ? new MealWeComConfig() : config;
        if (!SecurityUtils.isAdmin()) {
            query.setDeptId(SecurityUtils.getDeptId());
        }
        List<MealWeComConfig> list = mealWeComConfigMapper.selectMealWeComConfigList(query);
        list.forEach(this::maskWebhookKey);
        return list;
    }

    @Override
    public MealWeComConfig selectMealWeComConfigById(Long configId) {
        MealWeComConfig config = requireConfig(configId);
        MealScopeGuard.assertSameDept(config.getDeptId(), "企业微信通知配置");
        maskWebhookKey(config);
        return config;
    }

    @Override
    public int insertMealWeComConfig(MealWeComConfig config) {
        if (config == null) {
            throw new ServiceException("配置不能为空");
        }
        Long deptId = SecurityUtils.isAdmin() ? config.getDeptId() : SecurityUtils.getDeptId();
        if (deptId == null) {
            throw new ServiceException("家庭不能为空");
        }
        if (StringUtils.isEmpty(config.getWebhookKey()) || config.getWebhookKey().contains(MASK_MARK)) {
            throw new ServiceException("请填写企业微信群机器人 Webhook Key");
        }
        // 家庭与配置一对一：dept_id 唯一。已逻辑删除的旧行直接复用并恢复，避免唯一约束冲突
        MealWeComConfig existing = mealWeComConfigMapper.selectAnyByDeptId(deptId);
        if (existing != null && "0".equals(existing.getDelFlag())) {
            throw new ServiceException("该家庭已配置企业微信通知，请直接修改现有配置");
        }
        config.setDeptId(deptId);
        config.setWebhookKey(config.getWebhookKey().trim());
        config.setDelFlag("0");
        config.setCreateBy(SecurityUtils.getUsername());
        if (existing != null) {
            config.setConfigId(existing.getConfigId());
            config.setUpdateBy(SecurityUtils.getUsername());
            mealWeComConfigMapper.restoreMealWeComConfig(config);
            return 1;
        }
        config.setConfigId(null);
        return mealWeComConfigMapper.insertMealWeComConfig(config);
    }

    @Override
    public int updateMealWeComConfig(MealWeComConfig config) {
        if (config == null || config.getConfigId() == null) {
            throw new ServiceException("请选择要修改的配置");
        }
        MealWeComConfig exist = requireConfig(config.getConfigId());
        MealScopeGuard.assertSameDept(exist.getDeptId(), "企业微信通知配置");
        // 归属不允许通过修改接口迁移
        config.setDeptId(null);
        config.setUpdateBy(SecurityUtils.getUsername());
        if (isNewWebhookKey(config.getWebhookKey())) {
            config.setWebhookKey(config.getWebhookKey().trim());
        } else {
            config.setWebhookKey(null);
        }
        int rows = mealWeComConfigMapper.updateMealWeComConfig(config);
        if (config.getWebhookKey() != null) {
            mealWeComConfigMapper.updateWebhookKey(config.getConfigId(), config.getWebhookKey(),
                    SecurityUtils.getUsername());
        }
        return rows;
    }

    @Override
    public int deleteMealWeComConfigByIds(Long[] configIds) {
        if (configIds == null || configIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long configId : configIds) {
            MealWeComConfig config = requireConfig(configId);
            MealScopeGuard.assertSameDept(config.getDeptId(), "企业微信通知配置");
        }
        return mealWeComConfigMapper.deleteMealWeComConfigByIds(configIds);
    }

    @Override
    public String sendTestMessage(Long configId) {
        MealWeComConfig config = requireConfig(configId);
        MealScopeGuard.assertSameDept(config.getDeptId(), "企业微信通知配置");
        if (StringUtils.isEmpty(config.getWebhookKey())) {
            throw new ServiceException("该家庭尚未配置 Webhook Key");
        }
        String familyName = StringUtils.isNotEmpty(config.getDeptName()) ? config.getDeptName() : "当前家庭";
        String content = "【企业微信通知测试】\n" + familyName + " 的点餐通知已接通。";
        WeComRobotResponse response = weComRobotClient.sendText(config.getWebhookKey(), content);
        if (response.isSuccess()) {
            return "测试消息已发送";
        }
        throw new ServiceException("测试发送失败：" + StringUtils.nvl(response.getErrmsg(), "未知原因"));
    }

    /**
     * 查询配置并校验存在
     *
     * @param configId 配置ID
     * @return 配置
     */
    private MealWeComConfig requireConfig(Long configId) {
        if (configId == null) {
            throw new ServiceException("配置ID不能为空", HttpStatus.BAD_REQUEST);
        }
        MealWeComConfig config = mealWeComConfigMapper.selectMealWeComConfigById(configId);
        if (config == null) {
            throw new ServiceException("企业微信通知配置不存在或已删除");
        }
        return config;
    }

    /**
     * 把 key 替换为掩码
     *
     * @param config 配置
     */
    private void maskWebhookKey(MealWeComConfig config) {
        if (config != null) {
            config.setWebhookKey(WeComTextUtils.maskKey(config.getWebhookKey()));
        }
    }

    /**
     * 判断是否提交了新的 key
     *
     * @param webhookKey 入参 key
     * @return 需要覆盖原 key 时为 true
     */
    private boolean isNewWebhookKey(String webhookKey) {
        return StringUtils.isNotEmpty(webhookKey) && !webhookKey.contains(MASK_MARK);
    }
}
