package com.howe.admin.dao.login;

import com.howe.common.dto.login.LoginDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 14:42 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
@Mapper
public interface LoginDAO {

    List<LoginDTO> login();
}
