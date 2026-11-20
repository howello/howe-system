package com.howe.pregnancy.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 金额转十进制字符串的序列化器
 *
 * <p>金额固定保留两位小数（如 {@code 56.90}），与测量数值的「去尾零」不同：
 * 空值与 {@code 0.00} 在业务上必须可区分，展示也需要固定精度。</p>
 *
 * @author howe
 */
public class MoneyTextSerializer extends JsonSerializer<BigDecimal> {

    /**
     * 序列化为两位小数的十进制字符串
     *
     * @param value       金额
     * @param gen         生成器
     * @param serializers 序列化上下文
     * @throws IOException 写出失败
     */
    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }
}
