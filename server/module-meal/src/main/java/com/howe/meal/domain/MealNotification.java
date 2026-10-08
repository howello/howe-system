package com.howe.meal.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 点餐订单通知传输对象。
 */
@Data
@Schema(description = "点餐订单通知")
public class MealNotification {

    @Schema(description = "通知唯一ID")
    private String id;

    @Schema(description = "通知类型")
    private String type;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "附加信息")
    private Map<String, Object> extra;

    @JsonProperty("createTime")
    @Schema(description = "创建时间戳（毫秒）")
    private long createTime;
}
