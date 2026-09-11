package com.lims.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项（application.yml 的 lims.jwt.*）
 */
@Data
@ConfigurationProperties(prefix = "lims.jwt")
public class JwtProperties {

    /** HS256 密钥（>=32 字节；生产必须经 application-dev.yml 覆盖） */
    private String secret;

    /** access_token 有效期（秒） */
    private long accessTokenTtl = 7200L;

    /** refresh_token 有效期（秒） */
    private long refreshTokenTtl = 604800L;

    /** 携带 token 的请求头名 */
    private String header = "Authorization";

    /** token 前缀（含尾随空格） */
    private String tokenPrefix = "Bearer ";
}
