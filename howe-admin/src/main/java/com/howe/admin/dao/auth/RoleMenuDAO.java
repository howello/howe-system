package com.howe.admin.dao.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.dto.role.RoleMenuDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/13 17:31 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Mapper
public interface RoleMenuDAO extends BaseMapper<RoleMenuDTO> {
    List<MenuDTO> selectMenuListByRoleId(String roleId);
}
