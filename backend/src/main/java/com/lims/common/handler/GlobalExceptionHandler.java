package com.lims.common.handler;

import com.lims.common.R;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器：所有异常统一转换为 {@link R}，HTTP 状态保持 200，
 * 业务码经 body.code 传递（安全层 401/403 除外，见 security 包 EntryPoint/DeniedHandler）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（Service 层主动抛出） */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody DTO 校验失败（JSR-303） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return R.fail(ResultCode.BAD_REQUEST.getCode(), firstFieldError(e));
    }

    /** 表单对象绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        return R.fail(ResultCode.BAD_REQUEST.getCode(), firstFieldError(e));
    }

    /** @RequestParam/@PathVariable 单参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        return R.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.BAD_REQUEST.getCode(), "缺少必填参数: " + e.getParameterName());
    }

    /** 请求体 JSON 解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /** 无权限（@PreAuthorize 拒绝等，Controller 层抛出时兜底） */
    @ExceptionHandler(AccessDeniedException.class)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(ResultCode.FORBIDDEN);
    }

    /** 未认证兜底 */
    @ExceptionHandler(AuthenticationException.class)
    public R<Void> handleAuthentication(AuthenticationException e) {
        return R.fail(ResultCode.UNAUTHORIZED);
    }

    /** 路由不存在 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        return R.fail(404, "接口不存在: " + e.getRequestURL());
    }

    /** 系统异常兜底（不向前端暴露堆栈细节） */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.ERROR);
    }

    private String firstFieldError(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError == null) {
            return ResultCode.BAD_REQUEST.getMsg();
        }
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
}
