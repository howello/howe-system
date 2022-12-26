package com.howe.common.dao.flexConfig;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.flexConfig.ConfigDTO;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/15 9:23 星期四
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Mapper
public interface ConfigDAO extends BaseMapper<ConfigDTO> {
}
