package com.lims.common.exception;

import com.lims.common.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常
 *
 * <p>Service 层校验失败一律抛出本异常（禁止返回 null/错误码魔法值），
 * 由 {@code GlobalExceptionHandler} 统一转换为 {@code R}。
 * 用法：{@code throw new BizException("样品状态不允许分解")} 或指定业务码。</p>
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String msg) {
        this(ResultCode.ERROR.getCode(), msg);
    }

    public BizException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMsg());
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
