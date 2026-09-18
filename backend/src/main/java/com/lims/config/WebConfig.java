package com.lims.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 层配置：CORS + MVC 拦截器。
 *
 * <p>开发环境前端默认经 vite 代理（同源）访问，一般不触发跨域；
 * 此处放行本机前端端口，供绕过代理直连后端调试使用。
 * Bean 名固定为 corsConfigurationSource，Spring Security 会自动拾取。</p>
 *
 * <p>拦截器只登记 {@link OperationLogInterceptor}（操作日志审计），
 * 它内部自行判断「是否写请求 / 是否在跳过名单」，这里不做路径排除，
 * 保证日志规则集中在拦截器一处可读。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(operationLogInterceptor).addPathPatterns("/**");
    }

    /**
     * SSE 异步支持（feature A，T03）。
     *
     * <p>AI 流式对话（{@code POST /api/ai/chat/stream}）返回 {@code SseEmitter}，请求会进入
     * Servlet 异步模式。默认异步超时 30s，而 CPU 推理一条长回答可能数分钟，故显式放宽到
     * **300s**（与 {@code lims.ai.timeout-ms} 同量级）；超时后由容器完成 emitter，前端在
     * {@code fetch} 流上收到结束即可，不会挂死连接。</p>
     *
     * <p>注意：这里只放宽 MVC 异步超时，**不改**拦截器路径，日志规则仍集中在
     * {@link OperationLogInterceptor} 一处可读。</p>
     */
    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(300_000L);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
