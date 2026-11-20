package com.howe.pregnancy.domain;

/**
 * 固定指标的选项定义
 *
 * @param code  选项编码
 * @param label 选项标签
 * @param order 展示顺序
 * @author howe
 */
public record MetricOptionDef(String code, String label, int order) {
}
