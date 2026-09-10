package com.lims.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体（AGENTS.md 4.1）：{ "code": 0, "msg": "success", "data": {} }
 *
 * <p>Controller 一律返回 {@code R<T>}，禁止裸返回实体/Map。
 * 前端 axios 拦截器按 code!==0 统一报错、code===401 跳登录（见 frontend/src/utils/request.ts）。</p>
 */
@Data
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功，其余见 {@link ResultCode} */
    private Integer code;

    /** 提示信息（面向用户，可直接展示） */
    private String msg;

    /** 业务数据 */
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMsg(ResultCode.SUCCESS.getMsg());
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail(ResultCode.ERROR.getCode(), msg);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMsg());
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    /** 链式填充 data 的便捷方法 */
    public R<T> data(T data) {
        this.data = data;
        return this;
    }
}
