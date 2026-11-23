package com.howe.system.service;

import com.howe.common.exception.ServiceException;
import com.howe.common.notify.wecom.AppReleaseWebhookProvider;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.crypto.SecretCipher;
import com.howe.system.domain.AppReleaseNotifyConfig;
import com.howe.system.mapper.AppReleaseNotifyConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 维护应用独立的发版机器人配置。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppReleaseNotifyConfigService implements AppReleaseWebhookProvider {

    private final AppReleaseNotifyConfigMapper mapper;
    private final SecretCipher secretCipher;

    /** 查询应用的配置，只对外返回 Webhook Key 的掩码。 */
    public List<ConfigView> list(String appKey) {
        return mapper.selectByApp(appKey, false).stream()
                .map(config -> new ConfigView(config.getConfigId(), config.getLabel(), config.getEnabled(),
                        secretCipher.mask(config.getWebhookKey())))
                .toList();
    }

    /** 新建应用的通知目标，凭证加密入库。 */
    public int add(String appKey, AppReleaseNotifyConfig config) {
        if (StringUtils.isBlank(config.getWebhookKey())) {
            throw new ServiceException("请填写 Webhook Key");
        }
        normalize(config);
        return mapper.insert(appKey, config);
    }

    /** 修改指定应用的配置，Key 留空时继续使用原凭证。 */
    public int update(String appKey, Long configId, AppReleaseNotifyConfig config) {
        normalize(config);
        return mapper.update(appKey, configId, config);
    }

    /** 删除指定应用下的单条配置。 */
    public int delete(String appKey, Long configId) {
        return mapper.delete(appKey, configId);
    }

    /** 读取当前已启用的有效凭证，避免同一应用向同一机器人重复发送。 */
    @Override
    public List<String> findEnabledKeys(String appKey) {
        Set<String> keys = new LinkedHashSet<>();
        for (AppReleaseNotifyConfig config : mapper.selectByApp(appKey, true)) {
            try {
                String key = secretCipher.decrypt(config.getWebhookKey());
                if (StringUtils.isNotBlank(key)) {
                    keys.add(key);
                }
            } catch (ServiceException e) {
                log.warn("App 发版机器人凭证无法解密：appKey={}, configId={}", appKey, config.getConfigId());
            }
        }
        return List.copyOf(keys);
    }

    /** 规范化备注和凭证，加密非空凭证。 */
    private void normalize(AppReleaseNotifyConfig config) {
        config.setLabel(StringUtils.trimToEmpty(config.getLabel()));
        config.setWebhookKey(secretCipher.encrypt(StringUtils.trimToNull(config.getWebhookKey())));
    }

    /** 管理页使用的配置摘要，只包含 Webhook Key 掩码。 */
    public record ConfigView(Long configId, String label, Boolean enabled, String maskedKey) {
    }
}
