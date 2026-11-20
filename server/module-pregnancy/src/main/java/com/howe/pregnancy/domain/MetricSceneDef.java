package com.howe.pregnancy.domain;

/**
 * 固定指标的场景定义
 *
 * @param code  场景编码
 * @param label 场景标签
 * @param order 展示顺序
 * @author howe
 */
public record MetricSceneDef(String code, String label, int order) {
}
