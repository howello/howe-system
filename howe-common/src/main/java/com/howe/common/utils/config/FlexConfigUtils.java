package com.howe.common.utils.config;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ReflectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.common.dao.flexConfig.ConfigDAO;
import com.howe.common.dto.flexConfig.ConfigDTO;
import com.howe.common.dto.flexConfig.ConfigDataDTO;
import com.howe.common.dto.flexConfig.ConfigTransVO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.enums.flexConfig.FlexConfigBizTypeEnum;
import com.howe.common.enums.flexConfig.StatusEnum;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.CommonUtils;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.redis.RedisUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/22 8:59 星期二
 * <p>@Version 1.0
 * <p>@Description 灵活配置工具类
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlexConfigUtils {
    private final RedisUtils redisUtils;

    private final ConfigDAO configDAO;

    private final IdGeneratorUtil idGeneratorUtil;

    private final UserInfoUtils userInfoUtils;

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
    public void saveConfig(List<ConfigTransVO> configTransList) {
        String userName = userInfoUtils.getUserInfo().getUserName();
        Map<String, List<ConfigTransVO>> map = configTransList.stream()
                .collect(Collectors.groupingBy(ConfigTransVO::getBizTypeId));
        for (Map.Entry<String, List<ConfigTransVO>> entry : map.entrySet()) {
            ConfigDataDTO<Object> configDataDTO = new ConfigDataDTO<>(entry.getKey());
            List<ConfigTransVO> value = entry.getValue();
            for (ConfigTransVO configTransVO : value) {
                configDataDTO.addConfigItem(configTransVO.getKey(), configTransVO.getConfigDTO());
            }
            this.saveConfig(userName, configDataDTO);
        }
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

        ConfigDTO configDTO = new ConfigDTO();
        DateTime now = DateTime.now();
        configDTO.setRuleId(idGeneratorUtil.nextStr());
        configDTO.setEnable(StatusEnum.NORMAL.getCode());
        configDTO.setCreateTime(now);
        configDTO.setCreateBy(crteName);
        configDTO.setUpdateTime(now);
        configDTO.setUpdateBy(crteName);
        configDTO.setBizTypeId(type.getCode());
        configDTO.setBizTypeDesc(type.getDesc());
        configDTO.setData(JSONObject.toJSONString(configDataDTO.getConfigMap()));
        this.insertConfig(configDTO);
    }


    public void saveConfig(ConfigDTO configDTO) {
        String username = userInfoUtils.getUsername();
        DateTime now = DateTime.now();
        configDTO.setRuleId(idGeneratorUtil.nextStr());
        configDTO.setEnable(StatusEnum.NORMAL.getCode());
        configDTO.setCreateTime(now);
        configDTO.setCreateBy(username);
        configDTO.setUpdateTime(now);
        configDTO.setUpdateBy(username);
        this.insertConfig(configDTO);
    }

    /**
     * 更新配置（disable原有的，然后新插入一条，留作记录）
     *
     * @param configDataDTO
     */
    public void updateConfig(ConfigDTO configDTO) {
        UserDTO userInfo = userInfoUtils.getUserInfo();
        String userName = userInfo.getUserName();
        configDTO.setRuleId(idGeneratorUtil.nextStr());

        configDTO.setUpdateTime(DateTime.now());
        configDTO.setUpdateBy(userName);
        this.insertConfig(configDTO);
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
        ConfigDTO configDTO = this.getConfigOne(bizTypeId);
        if (configDTO == null) {
            throw new CommonException(CommonExceptionEnum.DATA_NOT_EXIST);
        }

        configDTO.setRuleId(String.valueOf(idGeneratorUtil.next()));
        configDTO.setEnable(StatusEnum.NORMAL.getCode());
        configDTO.setUpdateTime(DateTime.now());
        configDTO.setUpdateBy(updtName);
        configDTO.setData(JSONObject.toJSONString(configDataDTO));
        this.insertConfig(configDTO);
    }

    private void insertConfig(ConfigDTO configDTO) {
        this.disableAllConfigById(configDTO.getBizTypeId());
        configDAO.insert(configDTO);
        this.clearRedisConfigCache();
    }

    /**
     * 查询所有可用配置
     *
     * @return
     */
    public List<ConfigDTO> getConfigList(ConfigDTO configDTO) {
        QueryWrapper<ConfigDTO> qw = MybatisUtils.assembleQueryWrapper(configDTO);
        List<ConfigDTO> configDTOS = configDAO.selectList(qw);
        return configDTOS;
    }

    /**
     * 可用配置分页查询
     *
     * @param configDTO
     * @return
     */
    public PageInfo<ConfigDTO> getConfigPage(ConfigDTO configDTO) {
        PageHelper.startPage(configDTO.getPageNum(), configDTO.getPageSize());
        PageInfo<ConfigDTO> page = new PageInfo<>(this.getConfigList(configDTO));
        return page;
    }

    /**
     * 获取生效的唯一一条配置，有可能为null
     *
     * @param bizTypeId
     * @return
     */
    public ConfigDTO getConfigOne(String bizTypeId) {
        QueryWrapper<ConfigDTO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ConfigDTO.COL_ENABLE, StatusEnum.NORMAL.getCode());
        queryWrapper.eq(ConfigDTO.COL_BIZ_TYPE_ID, bizTypeId);
        ConfigDTO configDTO = configDAO.selectOne(queryWrapper);
        return configDTO;
    }

    /**
     * 根据ruleId使配置失效
     *
     * @param ruleIds
     */
    public void disableConfig(String[] ruleIds) {
        List<ConfigDTO> configDTOList = configDAO.selectBatchIds(Arrays.asList(ruleIds));
        for (ConfigDTO configDTO : configDTOList) {
            configDTO.setEnable(StatusEnum.DELETE.getCode());
            configDAO.updateById(configDTO);
        }
        this.clearRedisConfigCache();
    }


    /**
     * 配置失效
     *
     * @param configOne
     */
    public void disableConfig(ConfigDTO configOne) {
        if (configOne != null) {
            configOne.setEnable(StatusEnum.DELETE.getCode());
            configDAO.updateById(configOne);
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
        ConfigDTO flex = new ConfigDTO();
        flex.setEnable(StatusEnum.NORMAL.getCode());
        if (StringUtils.isNotBlank(bizTypeId)) {
            flex.setBizTypeId(bizTypeId);
        }
        List<ConfigDTO> configDTOList = this.getConfigList(flex);
        if (CollectionUtils.isNotEmpty(configDTOList)) {
            configDTOList.parallelStream().forEach(this::disableConfig);
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

        if (StringUtils.isBlank(key)) {
            key = "default";
        }

        T config = redisUtils.getObject(RedisKeyPrefixEnum.FLEX_CONFIG, bizTypeId + key, clazz);
        if (config == null) {
            ConfigDTO configOne = this.getConfigOne(bizTypeId);
            JSONObject json = JSONObject.parseObject(configOne.getData());
            config = json.getObject(key, clazz);
            T defaultConfig = json.getObject(ConfigDataDTO.DEFAULT_CONFIG_KEY, clazz);
            if (config == null) {
                config = defaultConfig;
            } else {
                Field[] fields = ReflectUtil.getFields(clazz);
                this.fillDefault(fields, defaultConfig, config);
            }
            redisUtils.set(RedisKeyPrefixEnum.FLEX_CONFIG, bizTypeId + key, config, 7, TimeUnit.DAYS);
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
     * 根据主键获取
     *
     * @param ruleId
     * @return
     */
    public ConfigDTO getConfigByRuleId(String ruleId) {
        return configDAO.selectById(ruleId);
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
    private <T> void fillDefault(Field[] fields, T source, T target) {
        for (Field field : fields) {
            String name = field.getName();
            Object fieldValue = ReflectUtil.getFieldValue(target, name);
            String s = field.toGenericString();
            if (s.contains(" static ") || s.contains(" final ") ||
                    s.contains(" volatile ") || s.contains(" transient ")) {
                continue;
            }

            if (fieldValue instanceof String && StringUtils.isBlank((String) fieldValue)) {
                fillDefault(source, target, field, name);
            } else if (fieldValue == null || CommonUtils.isPrimitive(field.getType())) {
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

}
