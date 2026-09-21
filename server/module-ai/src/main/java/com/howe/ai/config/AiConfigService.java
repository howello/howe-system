package com.howe.ai.config;

import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 配置服务
 *
 * <p>后台改完渠道、模型或场景路由后由这里触发 {@link AiConfigCache#refresh()}，
 * 下次调用立即生效，不需要重启。风格后缀模板也在这里按 key 从参数配置表取。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService
{
    private final AiConfigCache aiConfigCache;

    /**
     * 刷新配置缓存
     */
    public void refresh()
    {
        try
        {
            aiConfigCache.refresh();
        }
        catch (Exception e)
        {
            // 刷新失败不能让后台的保存操作回滚，保留旧快照继续服务
            log.error("刷新 AI 配置缓存失败，继续沿用上一次的快照", e);
        }
    }

    /**
     * 获取参数快照
     *
     * @return 参数快照
     */
    public AiProperties getProperties()
    {
        return aiConfigCache.getProperties();
    }

    /**
     * 读取风格后缀模板
     *
     * @param styleKey 风格 key，如 {@code recipe.photo}
     * @return 模板内容；未配置时返回空串
     */
    public String styleSuffix(String styleKey)
    {
        if (StrUtil.isBlank(styleKey))
        {
            return "";
        }
        String template = ConfigUtils.getString(ConfigConstants.AI_IMAGE_STYLE_SUFFIX_PREFIX + styleKey, "");
        return template == null ? "" : template.trim();
    }
}
