package com.lims.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer &lt;token&gt;，合法则把认证信息放入 SecurityContext。
 *
 * <p>T-102 落地：parse 出 username 后经 {@link UserDetailsServiceImpl} 装配 {@link LoginUser}，
 * 权限以数据库为权威源（token 内 perms claim 仅为签发时快照，不作鉴权依据）。
 * token 非法/过期/用户被删除：不阻断链路，保持匿名，由安全层 EntryPoint 统一回 401。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtTokenProvider.parseToken(token);
                String username = jwtTokenProvider.getUsername(claims);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                log.debug("JWT 校验未通过: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String headerValue = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getTokenPrefix();
        if (StringUtils.hasText(headerValue) && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length());
        }
        return null;
    }
}
