package com.howe.pregnancy.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 数值转十进制字符串的序列化器
 *
 * <p>按契约，测量数值与金额都以十进制字符串返回。直接 {@code ToStringSerializer}
 * 会带出数据库 {@code DECIMAL(12,2)} / {@code DECIMAL(24,6)} 的标度，
 * 让界面显示多余尾零。这里先 {@link BigDecimal#stripTrailingZeros()}，
 * 再以普通记数法输出，避免 {@code 1E+2} 这种科学计数。</p>
 *
 * @author howe
 */
public class NumericTextSerializer extends JsonSerializer<BigDecimal> {

    /**
     * 序列化为不带尾零的十进制字符串
     *
     * @param value       数值
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
        gen.writeString(value.stripTrailingZeros().toPlainString());
    }
}
