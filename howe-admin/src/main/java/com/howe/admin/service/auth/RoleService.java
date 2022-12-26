package com.howe.admin.service.auth;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.pagehelper.PageInfo;
import com.howe.common.dto.role.RoleDTO;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 18:19 星期四
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public interface RoleService extends IService<RoleDTO> {

    /**
     * 获取全部权限
     *
     * @return
     */
    List<RoleDTO> getRoleList(RoleDTO role);

    /**
     * 分页获取权限
     *
     * @return
     */
    PageInfo<RoleDTO> getRolePage(RoleDTO role);
}
