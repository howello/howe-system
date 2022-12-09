package com.howe.common.dao.dic;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.dic.DicDataDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/5 17:06 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Mapper
public interface DicDataDAO extends BaseMapper<DicDataDTO> {
    List<DicDataDTO> selectList(DicDataDTO dicData);
}
