package com.howe.common.exception.user;

/**
 * 用户不存在异常类
 *
 * @author howe
 */
public class UserNotExistsException extends UserException
{
    private static final long serialVersionUID = 1L;

    public UserNotExistsException()
    {
        super("user.not.exists", null);
    }
}
