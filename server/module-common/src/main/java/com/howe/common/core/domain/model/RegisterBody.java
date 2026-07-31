package com.howe.common.core.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户注册对象
 *
 * @author howe
 */
@Schema(description = "用户注册对象，字段与登录对象一致")
public class RegisterBody extends LoginBody
{

}
