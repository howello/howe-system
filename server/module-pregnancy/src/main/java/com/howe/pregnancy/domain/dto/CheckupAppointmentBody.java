package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产检预约请求
 *
 * @author howe
 */
@Data
@Schema(description = "产检预约请求")
public class CheckupAppointmentBody {

    @Schema(description = "预约日期")
    @NotNull(message = "预约日期不能为空")
    private String appointmentDate;

    @Schema(description = "预约时刻（HH:mm），可为空表示尚未确认具体时间")
    private String appointmentTime;

    @Schema(description = "医院")
    private String hospital;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "父版本")
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
