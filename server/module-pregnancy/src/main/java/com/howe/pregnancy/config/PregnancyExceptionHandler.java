package com.howe.pregnancy.config;

import com.howe.common.utils.StringUtils;
import com.howe.pregnancy.util.PregnancyException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 孕期接口异常处理
 *
 * <p>把业务异常转换为与 HTTP 状态同义的 {@code code}，并附带稳定的
 * {@code errorCode}、{@code requestId} 与字段错误列表，便于前端按语义分支处理。</p>
 *
 * <p><b>必须限定包范围：</b>不得去掉 {@code basePackages}。去掉后本类会接管
 * 整个应用的异常，把既有接口（登录、点餐、博客等）的错误体改写成孕期形状，
 * 属于跨模块回归。这里只处理 {@code com.howe.pregnancy} 下的 Controller。</p>
 *
 * <p><b>优先级：</b>实现 {@link Ordered} 并返回 {@code HIGHEST_PRECEDENCE}，
 * 使本处理器在全局 {@code GlobalExceptionHandler} 之前匹配。</p>
 *
 * <p>响应体不包含 SQL、磁盘路径、密钥或上游原始报文。</p>
 *
 * @author howe
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.howe.pregnancy")
public class PregnancyExceptionHandler implements Ordered {

    /** 请求 ID 请求头，与 API 契约一致 */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * 排序必须早于全局处理器
     *
     * @return 排序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 处理孕期业务异常
     *
     * @param e       业务异常
     * @param request 当前请求
     * @return 带 HTTP 状态的统一错误体
     */
    @ExceptionHandler(PregnancyException.class)
    public ResponseEntity<Map<String, Object>> handlePregnancy(PregnancyException e,
                                                               HttpServletRequest request) {
        int status = e.getHttpStatus();
        String requestId = StringUtils.isNotEmpty(e.getRequestId())
                ? e.getRequestId()
                : request.getHeader(REQUEST_ID_HEADER);
        log.warn("孕期接口业务异常：errorCode={}, status={}, requestId={}, message={}",
                e.getErrorCode(), status, requestId, e.getMessage());
        return ResponseEntity.status(status)
                .body(body(status, e.getMessage(), e.getErrorCode(), requestId, List.of(), e.getDetails()));
    }

    /**
     * 处理请求体字段校验失败
     *
     * @param e 校验异常
     * @return 400 与逐字段错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidBody(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(400, "请求参数有误", "VALIDATION_ERROR", null,
                        fieldErrors(e.getBindingResult().getFieldErrors())));
    }

    /**
     * 处理表单绑定失败
     *
     * @param e 绑定异常
     * @return 400 与逐字段错误
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBind(BindException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(400, "请求参数有误", "VALIDATION_ERROR", null,
                        fieldErrors(e.getBindingResult().getFieldErrors())));
    }

    /**
     * 处理权限不足
     *
     * @param e 授权异常
     * @return 403 统一错误体
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException e) {
        log.warn("孕期接口权限不足：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(403, "没有权限，请联系管理员授权", "FORBIDDEN", null, List.of()));
    }

    /**
     * 处理孕期接口内的未预期异常
     *
     * @param e 异常
     * @return 500 统一错误体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("孕期接口未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(500, "服务暂时不可用，请稍后重试", "INTERNAL_ERROR", null, List.of()));
    }

    /**
     * 转换字段错误
     *
     * @param errors 字段错误
     * @return 逐字段错误
     */
    private List<Map<String, String>> fieldErrors(List<FieldError> errors) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        for (FieldError error : errors) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("field", error.getField());
            item.put("message", error.getDefaultMessage());
            fieldErrors.add(item);
        }
        return fieldErrors;
    }

    /**
     * 组装统一错误体
     *
     * @param code        业务码
     * @param msg         可直接展示的中文提示
     * @param errorCode   稳定错误码
     * @param requestId   请求 ID
     * @param fieldErrors 字段错误列表
     * @return 响应体
     */
    private Map<String, Object> body(int code, String msg, String errorCode,
                                     String requestId, List<Map<String, String>> fieldErrors) {
        return body(code, msg, errorCode, requestId, fieldErrors, null);
    }

    /**
     * 组装统一错误体（含附加数据）
     *
     * @param code        业务码
     * @param msg         可直接展示的中文提示
     * @param errorCode   稳定错误码
     * @param requestId   请求 ID
     * @param fieldErrors 字段错误列表
     * @param details     附加数据，可为空
     * @return 响应体
     */
    private Map<String, Object> body(int code, String msg, String errorCode,
                                     String requestId, List<Map<String, String>> fieldErrors,
                                     Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("errorCode", errorCode);
        body.put("requestId", requestId);
        body.put("fieldErrors", fieldErrors);
        if (details != null && !details.isEmpty()) {
            body.putAll(details);
        }
        return body;
    }
}
