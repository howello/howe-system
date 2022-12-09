package com.howe.main.handler;

import cn.hutool.core.util.RandomUtil;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.exception.BaseException;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.request.AjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.security.SignatureException;
import java.util.List;
import java.util.StringJoiner;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:41 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
@ControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
    /**
     * 处理自定义的业务异常
     *
     * @param req
     * @param e
     * @return
     */
    @ExceptionHandler(value = BaseException.class)
    @ResponseBody
    public AjaxResult bizExceptionHandler(HttpServletRequest req, BaseException e) {
        String errorCode = RandomUtil.randomString(8);
        log.error("业务异常：{}", errorCode, e);
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理空指针的异常
     *
     * @param req
     * @param e
     * @return
     */
    @ExceptionHandler(value = NullPointerException.class)
    @ResponseBody
    public AjaxResult exceptionHandler(HttpServletRequest req, NullPointerException e) {
        log.error("发生空指针异常！原因是:", e);
        return AjaxResult.error(CommonExceptionEnum.NULL_POINT_ERROR);
    }

    /**
     * 处理@Valid的异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    @ResponseBody
    public AjaxResult methodArgExceptionHandle(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        StringJoiner sj = new StringJoiner(",");
        if (result.hasErrors()) {
            List<FieldError> fieldErrors = result.getFieldErrors();
            for (FieldError error : fieldErrors) {
                log.error("field:{}, msg:{}", error.getField(), error.getDefaultMessage(), e);
                sj.add(error.getDefaultMessage());
            }
        }
        return AjaxResult.error(sj.toString());
    }

    /**
     * 处理@Valid的异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(value = BindException.class)
    @ResponseBody
    public AjaxResult bindExceptionHandle(BindException e) {
        BindingResult result = e.getBindingResult();
        StringJoiner sj = new StringJoiner(",");
        if (result.hasErrors()) {
            List<FieldError> fieldErrors = result.getFieldErrors();
            for (FieldError error : fieldErrors) {
                log.error("field:{}, msg:{}", error.getField(), error.getDefaultMessage(), e);
                sj.add(error.getDefaultMessage());
            }
        }
        return AjaxResult.error(sj.toString());
    }

    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    @ResponseBody
    public AjaxResult methodNotSupport(HttpRequestMethodNotSupportedException e) {
        return AjaxResult.error(e.getMessage());
    }

    @ExceptionHandler(value = SignatureException.class)
    @ResponseBody
    public AjaxResult signatureException(SignatureException e) {
        e.printStackTrace();
        return AjaxResult.error(CommonExceptionEnum.TOKEN_ILLEGAL);
    }

    @ExceptionHandler(value = NumberFormatException.class)
    @ResponseBody
    public AjaxResult numberException(NumberFormatException e) {
        e.printStackTrace();
        return AjaxResult.error(CommonExceptionEnum.NUMBER_FORMAT_ERROR.getCode(),
                CommonException.assembleMsg(CommonExceptionEnum.NUMBER_FORMAT_ERROR,
                        e.getMessage()));
    }
}
