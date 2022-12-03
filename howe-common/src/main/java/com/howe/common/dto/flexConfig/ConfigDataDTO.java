package com.howe.common.dto.flexConfig;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.SneakyThrows;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/9 10:01 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public class ConfigDataDTO<T> {

    private final JSONObject config = new JSONObject();
    public static final String DEFAULT_CONFIG_KEY = "default";

    public ConfigDataDTO(String bizTypeId) {
        this.bizTypeId = bizTypeId;
    }

    private String bizTypeId;

    public ConfigDataDTO<T> setDefaultConfig(T defaultConfig) {
        return this.addConfigItem(ConfigDataDTO.DEFAULT_CONFIG_KEY, defaultConfig);
    }

    @SneakyThrows
    public ConfigDataDTO<T> addConfigItem(String key, T config) {
        if (StringUtils.isBlank(key)) {
            throw new Exception("key不能为空");
        }
        this.config.put(key, config);
        return this;
    }

    @SneakyThrows
    public void removeItemConfig(String key) {
        if (StringUtils.isBlank(key)) {
            throw new Exception("key不能为空");
        }
        this.config.remove(key);
    }

    public JSONObject getConfigMap() {
        return this.config;
    }

    public String getBizTypeId() {
        return this.bizTypeId;
    }
}
