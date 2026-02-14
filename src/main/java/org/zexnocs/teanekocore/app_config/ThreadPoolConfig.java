package org.zexnocs.teanekocore.app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 线程池配置类，负责配置应用程序中使用的线程池。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Configuration
public class ThreadPoolConfig {
    /**
     * 任务执行线程池，用于执行任务。
     * 核心线程数为 CPU 核心数，最大线程数为 CPU 核心数的两倍，队列大小为 CPU 核心数的 200 倍。
     *
     * @return 任务线程池
     */
    @Bean
    public ScheduledThreadPoolExecutor taskExecutor() {
        // 获取 cpu 核心数
        int cpu = Runtime.getRuntime().availableProcessors();

        // 创建 ScheduledThreadPoolExecutor，核心线程数为 cpu 核心数
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(cpu);

        // 设置最大线程数为 cpu 核心数的两倍
        executor.setMaximumPoolSize(cpu * 2);

        // 取消任务时会从队列中移除任务
        executor.setRemoveOnCancelPolicy(true);

        executor.setRejectedExecutionHandler((r, e) -> {
            // 保存被拒绝的任务
            // todo: 通知手动处理
            throw new RejectedExecutionException("Task executor is full");
        });

        return executor;
    }

    /**
     * 用于定时器的单线程 Scheduler。
     * @return 定时器 Scheduler
     */
    @Bean
    public ThreadPoolTaskScheduler timerScheduler() {
        // 创建一个单线程的 ThreadPoolTaskScheduler
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("timer-schedule-");
        return scheduler;
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
