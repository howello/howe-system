package com.howe.common.exception;


import cn.hutool.core.util.ArrayUtil;
import com.howe.common.enums.exception.BaseExceptionEnum;

import java.util.StringJoiner;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:43 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public class BaseException extends Exception {
    private static final long serialVersionUID = -7611643172712984323L;
    int code = -1;

    public BaseException() {
    }

    public BaseException(int code) {
        this.code = code;
    }

    public BaseException(String message) {
        super(message);
    }

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(Throwable cause) {
        super(cause);
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public static String assembleMsg(BaseExceptionEnum baseExceptionEnum, String... msg) {
        return BaseException.assembleMsg(baseExceptionEnum, 0, msg);
    }

    public static String assembleMsg(BaseExceptionEnum baseExceptionEnum, int index, String... msg) {
        int length = msg.length;
        index = Math.max(index, 0);
        index = Math.min(index, length);
        msg = ArrayUtil.insert(msg, index, baseExceptionEnum.getMessage());
        StringJoiner sj = new StringJoiner("，", "", "。");
        for (String s : msg) {
            sj.add(s);
        }
        return sj.toString();
    }
}
