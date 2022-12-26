package com.howe.admin.dao.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.common.dto.role.RoleDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 18:19 星期四
 * <p>@Version 1.0
 * <p>@Description 角色DAO
 */
@Mapper
public interface RoleDAO extends BaseMapper<RoleDTO> {
    List<RoleDTO> selectRoleListByUserId(String userId);
}
