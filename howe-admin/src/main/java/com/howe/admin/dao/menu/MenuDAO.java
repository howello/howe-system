package com.howe.admin.dao.menu;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.menu.MenuDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 14:19 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Mapper
public interface MenuDAO extends BaseMapper<MenuDTO> {
    List<String> selectMenuPermsByUserId(Long userId);

    List<MenuDTO> selectMenuListByUserId(Long userId);
}
