package com.lims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置（feature A，T03）。
 *
 * <p><b>为什么需要</b>：① GB 标准导入是**耗时 IO + 建索引**任务，必须异步（设计 §6.2，
 * 接口立即返回 jobId，前端轮询进度）；② AI 流式对话（SSE）需在**独立线程**上边收模型 token
 * 边推事件，请求线程要立刻返回 {@code SseEmitter}。</p>
 *
 * <p><b>为什么自定义线程池而不是默认 {@code SimpleAsyncTaskExecutor}</b>：默认实现**每次新建线程**、
 * 无上限，并发导入会打爆线程；此处给一个**有界**池 + 有界队列 + {@code CallerRunsPolicy} 背压，
 * 保证过载时降级而不是雪崩。</p>
 *
 * <p>命名 {@code aiTaskExecutor}：显式指定，避免与 Spring 默认 {@code taskExecutor} 混淆。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** AI 域专用执行器（KB 导入 + SSE 推送）。 */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心/最大线程：本机 AI 场景并发低（导入 + 少数会话），小而稳即可
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        // 线程前缀便于日志排查（如 ollama 卡住时定位是哪条任务）
        executor.setThreadNamePrefix("lims-ai-");
        // 过载背压：队列满则调用线程执行（导入接口会被拖慢但不会丢任务）；不用 AbortPolicy 直接失败
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关闭时等待在途任务完成，避免导入半途被中断留下 status=1 的僵尸 job
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
