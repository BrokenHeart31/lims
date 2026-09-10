package com.lims.common;

import lombok.Getter;

/**
 * 统一响应业务码（AGENTS.md 4.1）
 *
 * <p>0 成功；400 参数错误；401 未认证；403 无权限；500 系统异常。
 * 业务自定义错误码建议从 1000 起按模块分段，在 api-spec.md 登记。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    ERROR(500, "系统异常，请稍后重试");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
