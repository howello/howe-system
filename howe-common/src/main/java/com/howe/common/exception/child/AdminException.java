package com.howe.common.exception.child;

import com.howe.common.enums.exception.BaseExceptionEnum;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:46 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public class AdminException extends AppException {
    public AdminException(BaseExceptionEnum baseException) {
        super(baseException.getCode(), baseException.getMessage());
    }

    public AdminException(int code, String message) {
        super(code, message);
    }

    public AdminException(String message) {
        super(message);
    }
}
