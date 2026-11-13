package com.howe.ai.service;

import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import java.util.List;

/**
 * AI 用量统计的分页结果。
 *
 * <p>{@code total} 是聚合后的组数，不是原始调用记录数；展示名只补齐当前页。</p>
 *
 * @param items 当前页统计行
 * @param total 聚合组总数
 * @param hasMore 是否还有下一页
 */
public record AiStatsPage(List<AiCallLogStats> items, long total, boolean hasMore) {
}
