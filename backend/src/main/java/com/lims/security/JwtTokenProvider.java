package com.lims.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析（骨架）。
 *
 * <p>access_token 载荷：sub=username，perms=权限标识集合（resource:action）。
 * refresh_token 仅含 sub，用于换发新 access_token（T-102 登录接口落地时使用）。
 * 权限标识同时入 token 与 /me 响应：token 内副本供网管快速鉴权，
 * 权威权限以后端每次从 SecurityContext 校验为准。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    /** 权限标识集合在 claims 中的键名 */
    public static final String CLAIM_PERMISSIONS = "perms";

    private final JwtProperties properties;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 access_token */
    public String createAccessToken(String username, Collection<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getAccessTokenTtl() * 1000L);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_PERMISSIONS, permissions)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** 签发 refresh_token（不携带权限，避免权限变更后旧 refresh 越权） */
    public String createRefreshToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getRefreshTokenTtl() * 1000L);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 token（签名 + 过期）。
     *
     * @throws io.jsonwebtoken.JwtException 签名非法/过期/结构错误时抛出，调用方按未认证处理
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    /** 从 claims 提取权限标识集合；无此 claim（如 refresh_token）返回空表 */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        Object perms = claims.get(CLAIM_PERMISSIONS);
        if (perms instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public long getAccessTokenTtl() {
        return properties.getAccessTokenTtl();
    }
}
