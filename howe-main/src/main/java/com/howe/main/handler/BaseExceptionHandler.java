package com.howe.main.handler;

import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.utils.request.AjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:41 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
@ControllerAdvice
@Slf4j
@Order()
public class BaseExceptionHandler {
    /**
     * 处理其他异常
     *
     * @param req
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public AjaxResult exceptionHandler(HttpServletRequest req, Exception e) {
        log.error("未知异常！原因是:", e);
        return AjaxResult.error(CommonExceptionEnum.INTERNAL_SERVER_ERROR);
    }
}
