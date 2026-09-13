package com.lims.common.handler;

import com.lims.common.R;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器：所有异常统一转换为 {@link R}。
 *
 * <p><b>HTTP 状态约定（AGENTS 统一响应规范）</b>：
 * <ul>
 *   <li><b>业务异常</b>（{@link BizException}）→ HTTP <b>200</b> + {@code body.code} 传业务码；</li>
 *   <li><b>安全层拒绝</b>（{@link AccessDeniedException} 403 / {@link AuthenticationException} 401）→
 *       <b>带真实 HTTP 状态码</b>，与 {@code security} 包 EntryPoint/DeniedHandler 的 URL 级拒绝保持同一形态。</li>
 * </ul>
 *
 * <p>⚠️ 2026-09-13 修复：此前方法级鉴权（{@code @PreAuthorize}）的拒绝走本类兜底，
 * 返回的是 <b>HTTP 200 + body.code=403</b>，而 URL 级拒绝走 {@code RestAccessDeniedHandler}
 * 返回真 HTTP 403——<b>同一语义两种形态</b>，客户端无法仅凭 HTTP 状态判断鉴权结果（违反上述约定）。
 * 现统一为真 HTTP 状态码；前端两分支均可正确呈现（401→跳登录，403→提示「无访问权限」）。</p>
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

    /** 无权限（@PreAuthorize 拒绝等，Controller 层抛出时兜底）——带真 HTTP 403，与 URL 级拒绝同形态 */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(ResultCode.FORBIDDEN);
    }

    /** 未认证兜底——带真 HTTP 401，便于前端统一触发「重新登录」 */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
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
