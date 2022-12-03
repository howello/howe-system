package com.howe.common.utils.request;

import com.howe.common.constant.HttpStatus;
import com.howe.common.enums.exception.BaseExceptionEnum;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/7 17:32 星期一
 * <p>@Version 1.0
 * <p>@Description 公共返回包装类
 */
public class AjaxResult<T> implements Serializable {
    private int code = 0;
    private String message;
    private T data;

    public static String MSG_SUCCESS = "成功";
    public static String MSG_WARNING = "成功但有告警";
    public static String MSG_ERROR = "失败";

    public AjaxResult() {
    }

    public AjaxResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public AjaxResult(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 返回成功消息
     *
     * @return 成功消息
     */
    public static <T> AjaxResult<T> success() {
        return AjaxResult.success(MSG_SUCCESS);
    }

    /**
     * 返回成功消息
     *
     * @param msg 返回内容
     * @return 成功消息
     */
    public static <T> AjaxResult<T> success(String msg) {
        return AjaxResult.success(msg, null);
    }

    /**
     * 返回成功数据
     *
     * @return 成功消息
     */
    public static <T> AjaxResult<T> success(T data) {
        return AjaxResult.success(MSG_SUCCESS, data);
    }

    /**
     * 返回成功消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 成功消息
     */
    public static <T> AjaxResult<T> success(String msg, T data) {
        msg = StringUtils.isNotBlank(msg) ? msg : MSG_SUCCESS;
        return new AjaxResult(HttpStatus.SUCCESS, msg, data);
    }

    /**
     * 返回info消息
     *
     * @param code
     * @param message
     * @param <T>
     * @return
     */
    public static <T> AjaxResult<T> info(int code, String message) {
        return AjaxResult.info(code, message, null);
    }

    /**
     * 返回info消息
     *
     * @param code
     * @param message
     * @param data
     * @param <T>
     * @return
     */
    public static <T> AjaxResult<T> info(int code, String message, T data) {
        return new AjaxResult(code, message, data);
    }

    /**
     * 返回提醒数据
     *
     * @param data
     * @param <T>
     * @return
     */
    public static <T> AjaxResult<T> warning(T data) {
        return AjaxResult.warning(MSG_WARNING, data);
    }

    /**
     * 返回提醒内容
     *
     * @param message
     * @param <T>
     * @return
     */
    public static <T> AjaxResult<T> warning(String message) {
        return AjaxResult.warning(message, null);
    }

    /**
     * 返回提醒内容
     *
     * @param msg
     * @param data
     * @param <T>
     * @return
     */
    public static <T> AjaxResult<T> warning(String msg, T data) {
        msg = StringUtils.isNotBlank(msg) ? msg : MSG_WARNING;
        return new AjaxResult(HttpStatus.WARRING, msg, data);
    }

    /**
     * 返回失败消息
     *
     * @param baseExceptionEnum 返回内容
     * @return 消息
     */
    public static <T> AjaxResult<T> error(BaseExceptionEnum baseExceptionEnum) {
        return AjaxResult.error(baseExceptionEnum.getCode(), baseExceptionEnum.getMessage());
    }


    /**
     * 返回失败消息
     *
     * @param msg 返回内容
     * @return 消息
     */
    public static <T> AjaxResult<T> error(int code, String msg) {
        return AjaxResult.error(code, msg, null);
    }


    /**
     * 返回失败消息
     *
     * @param msg 返回内容
     * @return 消息
     */
    public static <T> AjaxResult<T> error(String msg) {
        return AjaxResult.error(msg, null);
    }

    /**
     * 返回失败数据
     *
     * @return 消息
     */
    public static <T> AjaxResult<T> error(T data) {
        return AjaxResult.error(MSG_ERROR, data);
    }

    /**
     * 返回失败消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 消息
     */
    public static <T> AjaxResult<T> error(String msg, T data) {
        return AjaxResult.error(HttpStatus.ERROR, msg, data);
    }

    /**
     * 返回失败消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 消息
     */
    public static <T> AjaxResult<T> error(int code, String msg, T data) {
        msg = StringUtils.isNotBlank(msg) ? msg : MSG_ERROR;
        return new AjaxResult(code, msg, data);
    }


    public int getCode() {
        return this.code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
