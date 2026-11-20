package com.howe.pregnancy.domain;

import java.util.List;

/**
 * 固定指标的字段定义
 *
 * @param code    字段编码
 * @param label   字段标签
 * @param type    字段类型（DECIMAL 数值 / CHOICE 选项 / TEXT 文本）
 * @param required 是否必填
 * @param units   可选单位
 * @param scale   数值精度
 * @param options 选项定义，非 CHOICE 字段为 null
 * @param summable 是否参与按日求和
 * @param secondary 是否写入次数值列
 * @author howe
 */
public record MetricFieldDef(String code, String label, String type, boolean required,
                             List<String> units, int scale, List<MetricOptionDef> options,
                             boolean summable, boolean secondary) {
}
