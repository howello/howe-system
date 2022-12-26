package com.howe.common.dto.flexConfig;

import lombok.Data;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/15 9:57 星期四
 * <p>@Version 1.0
 * <p>@Description 前后端传输使用的DTO
 */
@Data
public class ConfigTransVO {

    /**
     * 业务id
     */
    private String bizTypeId;

    /**
     * 对应的键值
     */
    private String key;

    /**
     * 对应的数据
     */
    private ConfigDTO configDTO;
}
