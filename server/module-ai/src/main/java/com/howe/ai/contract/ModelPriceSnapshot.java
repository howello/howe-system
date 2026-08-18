package com.howe.ai.contract;

import java.math.BigDecimal;

public record ModelPriceSnapshot(String model, BigDecimal inputPrice, BigDecimal outputPrice,
                                 String currency) {
    public ModelPriceSnapshot {
        if (model == null || model.isBlank() || inputPrice == null || outputPrice == null
            || currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("模型价格字段不能为空");
        }
        if (inputPrice.signum() < 0 || outputPrice.signum() < 0) {
            throw new IllegalArgumentException("模型价格不能为负数");
        }
    }
}
