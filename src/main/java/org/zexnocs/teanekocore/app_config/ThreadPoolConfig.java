package org.zexnocs.teanekocore.app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.RejectedExecutionException;

/**
 * 线程池配置类，负责配置应用程序中使用的线程池。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Configuration
public class ThreadPoolConfig {
    /**
     * 配置一个 ThreadPoolTaskExecutor 线程池，
     * 核心线程数为 CPU 核心数，最大线程数为 CPU 核心数的两倍，队列大小为 CPU 核心数的 200 倍。
     *
     * @return {@link ThreadPoolTaskExecutor }
     */
    @Bean
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        var availableProcessors = Runtime.getRuntime().availableProcessors();

        // 核心线程数
        executor.setCorePoolSize(availableProcessors);

        // 最大线程数
        executor.setMaxPoolSize(availableProcessors * 2);

        // 队列大小
        executor.setQueueCapacity(availableProcessors * 200);

        // 拒绝策略
        executor.setRejectedExecutionHandler(((r, executor1) -> {
            // 保存被拒绝的任务
            // todo: 通知手动处理
            throw new RejectedExecutionException("Thread pool is full");
        }));

        executor.initialize();
        return executor;
    }

    /**
     * 专门为 api 请求设计的 Scheduler
     */
    @Bean
    public Scheduler apiScheduler() {
        var availableProcessors = Runtime.getRuntime().availableProcessors();

        return Schedulers.newBoundedElastic(
                availableProcessors * 2,
                availableProcessors * 50,
                "api-scheduler"
        );
    }
}
