package com.howe.common.exception.child;

import com.howe.common.exception.BaseException;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:44 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public class AppException extends BaseException {
    private static final long serialVersionUID = -3232706417844019328L;

    public AppException() {
    }

    public AppException(int code) {
        super(code);
    }

    public AppException(String message) {
        super(message);
    }

    public AppException(int code, String message) {
        super(code, message);
    }

    public AppException(Throwable cause) {
        super(cause);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }

    public AppException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
