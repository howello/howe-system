package com.howe.common.utils.dic;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.common.annotation.Cache;
import com.howe.common.dao.dic.DicDataDAO;
import com.howe.common.dao.dic.DictTypeDAO;
import com.howe.common.dto.dic.DicDataDTO;
import com.howe.common.dto.dic.DictTypeDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.StatusEnum;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.enums.redis.RedisTypeEnum;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.redis.RedisUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/5 17:10 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Component
@RequiredArgsConstructor
public class DictionaryUtils {

    private final DicDataDAO dicDataDAO;

    private final DictTypeDAO dictTypeDAO;

    private final UserInfoUtils userInfoUtils;

    private final RedisUtils redisUtils;

    private final IdGeneratorUtil idGeneratorUtil;


    /**
     * 根据条件查询字典列表
     *
     * @param dicData
     * @return
     */
    public List<DicDataDTO> getDicList(DicDataDTO dicData) {
        QueryWrapper<DicDataDTO> qw = MybatisUtils.assembleQueryWrapper(dicData);
        return dicDataDAO.selectList(qw);
    }

    /**
     * 根据条件查询字典列表--分页
     *
     * @param dicData
     * @return
     */
    public PageInfo<DicDataDTO> getDicPage(DicDataDTO dicData) {
        PageHelper.startPage(dicData.getPageNum(), dicData.getPageSize());
        List<DicDataDTO> dicList = this.getDicList(dicData);
        return new PageInfo<>(dicList);
    }

    /**
     * 根据类型和值获取label
     *
     * @param dicType
     * @param value
     * @return
     */
    public String getLabel(String dicType, String value) {
        DicDataDTO dicDataDTO = new DicDataDTO();
        dicDataDTO.setDictType(dicType);
        dicDataDTO.setDictValue(value);
        dicDataDTO.setStatus(StatusEnum.DELETE.getCodeStr());
        List<DicDataDTO> dicList = this.getDicList(dicDataDTO);
        if (CollectionUtils.isNotEmpty(dicList)) {
            return dicList.get(0).getDictLabel();
        }
        return "";
    }

    /**
     * 根据字典类型获取字典数据列表
     *
     * @param dicType
     * @return
     */
    @SneakyThrows
    @Cache(prefix = RedisKeyPrefixEnum.DIC_DATA_INFO,
            key = "dicType",
            redisType = RedisTypeEnum.LIST,
            clazz = DicDataDTO.class,
            time = 1,
            timeUnit = TimeUnit.DAYS)
    public List<DicDataDTO> getDicDataListByType(String dicType) {
        if (StringUtils.isBlank(dicType)) {
            throw new CommonException("字典类型不可为空！");
        }
        DicDataDTO dicDataDTO = new DicDataDTO();
        dicDataDTO.setDictType(dicType);
        return this.getDicList(dicDataDTO);
    }

    /**
     * 根据Id获取字典数据
     *
     * @param dicId
     * @return
     */
    @Cache(prefix = RedisKeyPrefixEnum.DIC_DATA_INFO,
            key = "dicId",
            redisType = RedisTypeEnum.OBJECT,
            clazz = DicDataDTO.class,
            time = 1,
            timeUnit = TimeUnit.DAYS)
    public DicDataDTO getDicById(String dicId) {
        return dicDataDAO.selectById(dicId);
    }

    /**
     * 保存字典
     *
     * @param dicDataDTO
     * @return
     */
    public int saveDicData(DicDataDTO dicDataDTO) {
        String id = idGeneratorUtil.nextStr();
        dicDataDTO.setDictCode(id);
        return dicDataDAO.insert(dicDataDTO);
    }

    /**
     * 更新字典
     *
     * @param dicDataDTO
     * @return
     */
    public int updateDicDate(DicDataDTO dicDataDTO) {
        this.refreshCache();
        return dicDataDAO.updateById(dicDataDTO);
    }

    /**
     * 主键批量删除
     *
     * @param dictCodes
     * @return
     */
    public int delDicData(String[] dictCodes) {
        this.refreshCache();
        return dicDataDAO.deleteBatchIds(Arrays.asList(dictCodes));
    }

    /**
     * 根据条件查询所有字典类型列表
     *
     * @param dictType
     * @return
     */
    public List<DictTypeDTO> getDicTypeList(DictTypeDTO dictType) {
        QueryWrapper<DictTypeDTO> qw = MybatisUtils.assembleQueryWrapper(dictType);
        return dictTypeDAO.selectList(qw);
    }

    /**
     * 根据条件查询所有字典类型列表
     *
     * @param dictType
     * @return
     */
    public PageInfo<DictTypeDTO> getDicTypePage(DictTypeDTO dictType) {
        PageHelper.startPage(dictType.getPageNum(), dictType.getPageSize());
        List<DictTypeDTO> dicTypeList = this.getDicTypeList(dictType);
        return new PageInfo<>(dicTypeList);
    }

    /**
     * 根据id获取字典类型
     *
     * @param dicTypeId
     * @return
     */
    @Cache(prefix = RedisKeyPrefixEnum.DIC_DATA_INFO,
            key = "dicTypeId",
            redisType = RedisTypeEnum.OBJECT,
            clazz = DictTypeDTO.class,
            time = 1,
            timeUnit = TimeUnit.DAYS)
    public DictTypeDTO getDicTypeById(String dicTypeId) {
        return dictTypeDAO.selectById(dicTypeId);
    }

    /**
     * 更新字典类型
     *
     * @param dictType
     * @return
     */
    public int updateType(DictTypeDTO dictType) {
        this.refreshCache();
        return dictTypeDAO.updateById(dictType);
    }

    /**
     * 插入字典类型
     *
     * @param dictType
     * @return
     */
    public int saveDicType(DictTypeDTO dictType) {
        String id = idGeneratorUtil.nextStr();
        DateTime now = DateTime.now();
        UserDTO userInfo = userInfoUtils.getUserInfo();
        String userName = userInfo.getUserName();
        dictType.setDictId(id);
        dictType.setCreateBy(userName);
        dictType.setCreateTime(now);
        dictType.setUpdateBy(userName);
        dictType.setUpdateTime(now);
        return dictTypeDAO.insert(dictType);
    }

    /**
     * 删除字典类型，
     * 如有已分配的字典数据，会报错
     *
     * @param dicTypeIds
     * @return
     */
    @SneakyThrows
    public int delDicType(String[] dicTypeIds) {
        DicDataDTO dicDataDTO = new DicDataDTO();
        for (String dicTypeId : dicTypeIds) {
            DictTypeDTO dictTypeDTO = dictTypeDAO.selectById(dicTypeId);
            dicDataDTO.setDictType(dictTypeDTO.getDictType());
            QueryWrapper<DicDataDTO> qw = MybatisUtils.assembleQueryWrapper(dicDataDTO);
            List<DicDataDTO> dicDataList = dicDataDAO.selectList(qw);
            if (CollectionUtils.isNotEmpty(dicDataList)) {
                throw new CommonException(CommonExceptionEnum.DIC_TYPE_HAS_DISTRIBUTION);
            }
            dictTypeDAO.deleteById(dicTypeId);
        }
        this.refreshCache();
        return dicTypeIds.length;
    }

    /**
     * TODO
     * 刷新字典缓存
     *
     * @return
     */
    public Boolean refreshCache() {
        redisUtils.deleteKeys(RedisKeyPrefixEnum.DIC_DATA_INFO);
        return true;
    }
}
