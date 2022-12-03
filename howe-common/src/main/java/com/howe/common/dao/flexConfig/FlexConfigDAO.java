package com.howe.common.dao.flexConfig;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.flexConfig.FlexConfigDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/9 10:10 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
@Mapper
public interface FlexConfigDAO extends BaseMapper<FlexConfigDTO> {

    /**
     * 查询列表
     *
     * @param flexConfigDTO
     * @return
     */
    List<FlexConfigDTO> selectList(FlexConfigDTO flexConfigDTO);
}
