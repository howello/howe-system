package com.howe.ai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 配置服务
 *
 * <p>后台改完渠道、模型或场景路由后由这里触发 {@link AiConfigCache#refresh()}，
 * 下次调用立即生效，不需要重启。</p>
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
}
