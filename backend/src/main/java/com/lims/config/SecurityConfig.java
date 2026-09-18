package com.lims.config;

import com.lims.security.JwtAuthenticationFilter;
import com.lims.security.JwtProperties;
import com.lims.security.RestAccessDeniedHandler;
import com.lims.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（AGENTS.md 4.1/8.3/8.4）
 *
 * <p>无状态 JWT：关闭 session 与 csrf；/auth/login、/auth/refresh 放行，
 * 其余接口一律认证后按 @PreAuthorize("hasAuthority('权限标识')") 二次鉴权。
 * 安全红线：权限判定只在后端，前端菜单/按钮显隐仅是体验层。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /** 无需认证即可访问的路径（相对 context-path /api） */
    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/auth/refresh",
            "/error"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT 无状态：不使用 session，不启用 csrf
                .csrf(AbstractHttpConfigurer::disable)
                // 拾取 WebConfig 中的 corsConfigurationSource Bean
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // -----------------------------------------------------------------
                        // ASYNC / ERROR 派发放行（2026-09-18 修复：SSE 流式回答永久卡在「正在生成」）
                        // -----------------------------------------------------------------
                        // 为什么必须放行：SseEmitter 完成时会触发一次**容器内部的 ASYNC 派发**，
                        // 该派发会**重跑整条过滤器链与拦截器链**；而 JwtAuthenticationFilter 继承
                        // OncePerRequestFilter，**默认跳过 ASYNC 派发**，此时 SecurityContext 变为
                        // 匿名 ⇒ AuthorizationFilter 抛 AccessDeniedException；可 SSE 响应早已提交
                        // （text/event-stream 已开流），异常无法转为错误响应 ⇒ 连接被强行掐断
                        // ⇒ 前端 read() 永久挂起、收不到 done 帧 ⇒ 界面卡在「正在生成」。
                        //
                        // 安全性：ASYNC 派发是容器对「**已经通过 REQUEST 派发鉴权**」的同一请求的续跑，
                        // 客户端无法伪造 DispatcherType，故放行不会让任何接口对匿名用户开放；
                        // REQUEST 派发仍是全量鉴权（下一条 anyRequest().authenticated()）。
                        // ERROR 派发放行同理：/error 本就是中转路径（见 PUBLIC_PATHS）。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 密码加密器：BCrypt（AGENTS.md 1 安全要求） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
