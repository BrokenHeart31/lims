package com.lims.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录/刷新令牌响应（api-spec 1.1/1.2）
 */
@Data
@AllArgsConstructor
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String accessToken;

    private String refreshToken;

    /** accessToken 有效期（秒） */
    private long expiresIn;
}
