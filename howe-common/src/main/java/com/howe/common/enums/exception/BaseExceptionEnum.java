package com.howe.common.enums.exception;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 11:12 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public interface BaseExceptionEnum {
    public default int getCode() {
        return -1;
    }

    public default String getMessage() {
        return "";
    }
}
