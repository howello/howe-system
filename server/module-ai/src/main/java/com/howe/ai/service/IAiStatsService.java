package com.howe.ai.service;

import com.howe.ai.persistence.domain.vo.AiChannelHealth;
import com.howe.ai.persistence.domain.vo.AiHomeStats;
import com.howe.ai.persistence.domain.vo.AiImageAssetOverview;

/**
 * AI 首页统计服务
 *
 * <p>聚合口径与降级封装在本服务内：AI 用量复用既有调用记录的聚合能力，
 * 渠道健康与图片资产按既有字段口径统计。</p>
 *
 * @author howe
 */
public interface IAiStatsService {

    /**
     * 首页 AI 用量（今日调用、token 消耗、成功率、按场景分布）
     *
     * @return AI 用量
     */
    AiHomeStats getHomeStats();

    /**
     * 渠道健康（各渠道状态、异常数、停用数、最近检测时间）
     *
     * @return 渠道健康
     */
    AiChannelHealth getChannelHealth();

    /**
     * 图片资产概况（总数、占用空间、最近生成时间）
     *
     * @return 图片资产概况
     */
    AiImageAssetOverview getImageAssetOverview();
}
