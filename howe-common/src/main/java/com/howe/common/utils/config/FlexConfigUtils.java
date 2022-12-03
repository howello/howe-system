package com.howe.common.utils.config;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ReflectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.howe.common.constant.ListConstant;
import com.howe.common.dao.flexConfig.FlexConfigDAO;
import com.howe.common.dto.flexConfig.ConfigDataDTO;
import com.howe.common.dto.flexConfig.FlexConfigDTO;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.enums.flexConfig.FlexConfigBizTypeEnum;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.redis.RedisUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/22 8:59 星期二
 * <p>@Version 1.0
 * <p>@Description 灵活配置工具类
 */
@Component
@Slf4j
public class FlexConfigUtils {
    @Resource
    private RedisUtils redisUtils;

    @Resource
    private FlexConfigDAO flexConfigDAO;


    @Resource
    private IdGeneratorUtil idGeneratorUtil;

    @PostConstruct
    public void init() {
        log.info("开机启动，初始化配置缓存");
        this.clearRedisConfigCache();
        log.info("开机启动，初始化配置缓存完成");
    }


    /**
     * 保存配置
     *
     * @param crteName
     * @param configDataDTO
     */
    @SneakyThrows
    public void saveConfig(String crteName, ConfigDataDTO configDataDTO) {
        if (StringUtils.isBlank(configDataDTO.getBizTypeId())) {
            throw new CommonException(CommonExceptionEnum.BIZ_TYPE_ID_NULL);
        }
        FlexConfigBizTypeEnum type = FlexConfigBizTypeEnum.getTypeById(configDataDTO.getBizTypeId());
        if (type == null) {
            throw new CommonException(CommonExceptionEnum.BIZ_TYPE_DOES_NOT_EXIST);
        }

        FlexConfigDTO flexConfigDTO = new FlexConfigDTO();
        DateTime now = DateTime.now();
        flexConfigDTO.setRuleId(String.valueOf(idGeneratorUtil.next()));
        flexConfigDTO.setEnable(true);
        flexConfigDTO.setCrteTime(now);
        flexConfigDTO.setCrteName(crteName);
        flexConfigDTO.setUpdtTime(now);
        flexConfigDTO.setUpdtName(crteName);
        flexConfigDTO.setBizTypeId(type.getCode());
        flexConfigDTO.setBizTypeDesc(type.getDesc());
        flexConfigDTO.setData(configDataDTO.getConfigMap().toJSONString());
        this.insertConfig(flexConfigDTO);
    }

    /**
     * 更新配置（disable原有的，然后新插入一条，留作记录）
     *
     * @param updtName
     * @param configDataDTO
     */
    @SneakyThrows
    public void updateConfig(String updtName, ConfigDataDTO configDataDTO) {
        String bizTypeId = configDataDTO.getBizTypeId();
        FlexConfigDTO flexConfigDTO = this.getConfigOne(bizTypeId);
        if (flexConfigDTO == null) {
            throw new CommonException(CommonExceptionEnum.DATA_NOT_EXIST);
        }

        flexConfigDTO.setRuleId(String.valueOf(idGeneratorUtil.next()));
        flexConfigDTO.setEnable(true);
        flexConfigDTO.setUpdtTime(DateTime.now());
        flexConfigDTO.setUpdtName(updtName);
        flexConfigDTO.setData(JSONObject.toJSONString(configDataDTO));
        this.insertConfig(flexConfigDTO);
    }

    private void insertConfig(FlexConfigDTO flexConfigDTO) {
        this.disableAllConfigById(flexConfigDTO.getBizTypeId());
        flexConfigDAO.insert(flexConfigDTO);
        this.clearRedisConfigCache();
    }

    /**
     * 查询所有可用配置
     *
     * @return
     */
    public List<FlexConfigDTO> getConfigList(FlexConfigDTO flexConfigDTO) {
        List<FlexConfigDTO> flexConfigDTOS = flexConfigDAO.selectList(flexConfigDTO);
        return flexConfigDTOS;
    }

    /**
     * 可用配置分页查询
     *
     * @param page
     * @return
     */
    public Page<FlexConfigDTO> getConfigPage(Page<FlexConfigDTO> page) {
        QueryWrapper<FlexConfigDTO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ENABLE", true);
        page = flexConfigDAO.selectPage(page, queryWrapper);
        return page;
    }

    /**
     * 获取生效的唯一一条配置，有可能为null
     *
     * @param bizTypeId
     * @return
     */
    public FlexConfigDTO getConfigOne(String bizTypeId) {
        QueryWrapper<FlexConfigDTO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ENABLE", true);
        queryWrapper.eq("BIZ_TYPE_ID", bizTypeId);
        FlexConfigDTO flexConfigDTO = flexConfigDAO.selectOne(queryWrapper);
        return flexConfigDTO;
    }

    /**
     * 配置失效
     *
     * @param configOne
     */
    public void disableConfig(FlexConfigDTO configOne) {
        if (configOne != null) {
            configOne.setEnable(false);
            flexConfigDAO.updateById(configOne);
            this.clearRedisConfigCache();
        }
    }

    /**
     * id下的所有配置失效
     * id如果传null，就会让所有配置失效
     *
     * @param bizTypeId id
     */
    public void disableAllConfigById(String bizTypeId) {
        //查库里是否有重复
        FlexConfigDTO flex = new FlexConfigDTO();
        flex.setEnable(true);
        if (StringUtils.isNotBlank(bizTypeId)) {
            flex.setBizTypeId(bizTypeId);
        }
        List<FlexConfigDTO> flexConfigDTOList = this.getConfigList(flex);
        if (CollectionUtils.isNotEmpty(flexConfigDTOList)) {
            flexConfigDTOList.parallelStream().forEach(this::disableConfig);
        }
    }

    /**
     * 获取唯一配置
     *
     * @param bizTypeId
     * @param key
     * @param clazz
     * @param <T>
     * @return
     */
    @SneakyThrows
    public <T> T getConfig(String bizTypeId, String key, Class<T> clazz) {
        if (StringUtils.isBlank(bizTypeId)) {
            throw new CommonException(CommonExceptionEnum.BIZ_TYPE_ID_NULL);
        }

        T config = redisUtils.getObject(RedisKeyPrefixEnum.FLEX_CONFIG, bizTypeId + key, clazz);
        if (config == null) {
            FlexConfigDTO configOne = this.getConfigOne(bizTypeId);
            JSONObject json = JSONObject.parseObject(configOne.getData());
            config = json.getObject(key, clazz);
            T defaultConfig = json.getObject(ConfigDataDTO.DEFAULT_CONFIG_KEY, clazz);
            if (config == null) {
                config = defaultConfig;
            } else {
                Field[] fields = ReflectUtil.getFields(clazz);
                this.fillDefault(fields, defaultConfig, config);
                redisUtils.set(RedisKeyPrefixEnum.FLEX_CONFIG, bizTypeId + key, config, 7, TimeUnit.DAYS);
            }
        }
        return config;
    }

    /**
     * 获取唯一配置
     *
     * @param bizTypeId
     * @param clazz
     * @param <T>
     * @return
     */
    @SneakyThrows
    public <T> T getConfig(String bizTypeId, Class<T> clazz) {
        return this.getConfig(bizTypeId, null, clazz);
    }

    /**
     * 清空redis里的配置
     */
    public void clearRedisConfigCache() {
        redisUtils.deleteKeys(RedisKeyPrefixEnum.FLEX_CONFIG);
    }

    /**
     * 填充默认数据
     *
     * @param fields
     * @param source
     * @param target
     * @param <T>
     */
    @SneakyThrows
    public <T> void fillDefault(Field[] fields, T source, T target) {
        for (Field field : fields) {
            String name = field.getName();
            Object fieldValue = ReflectUtil.getFieldValue(target, name);
            String s = field.toGenericString();
            if (s.contains(" static ") || s.contains(" final ") ||
                    s.contains(" volatile ") || s.contains(" transient ")) {
                continue;
            }
            if (fieldValue == null || isPrimitive(field.getType())) {
                fillDefault(source, target, field, name);
            } else if (fieldValue instanceof String && StringUtils.isBlank((String) fieldValue)) {
                fillDefault(source, target, field, name);
            } else if (fieldValue instanceof Collection && CollectionUtils.isEmpty((Collection<?>) fieldValue)) {
                fillDefault(source, target, field, name);
            } else if (fieldValue instanceof Arrays && ArrayUtils.isEmpty((Object[]) fieldValue)) {
                fillDefault(source, target, field, name);
            } else {
                Field[] childFields = ReflectUtil.getFields(field.getType());
                Object defaultValue = ReflectUtil.getFieldValue(source, name);
                fillDefault(childFields, defaultValue, fieldValue);
            }
        }
    }

    /**
     * 设置默认值
     *
     * @param source 默认数据
     * @param target 填充目标数据
     * @param field
     * @param name
     * @param <T>
     * @throws IllegalAccessException
     */
    private <T> void fillDefault(T source, T target, Field field, String name) throws IllegalAccessException {
        Object defaultValue = ReflectUtil.getFieldValue(source, name);
        field.setAccessible(true);
        field.set(target, defaultValue);
    }

    /**
     * 校验是否为基本类型
     *
     * @param type
     * @return
     */
    private boolean isPrimitive(Class<?> type) {
        String typeName = type.getSimpleName();
        return ListConstant.PRIMITIVE_LIST.contains(typeName);
    }
}
