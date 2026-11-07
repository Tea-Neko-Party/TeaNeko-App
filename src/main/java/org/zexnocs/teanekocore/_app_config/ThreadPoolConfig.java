package org.zexnocs.teanekocore._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 线程池配置类，负责配置应用程序中使用的线程池。
 *
 * <br>2026/11/07：明确调度队列边界，准入限制由业务入口负责。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.8.7-l
 */
@Configuration
public class ThreadPoolConfig {
    /**
     * 任务执行线程池，用于执行任务。
     * 2026/11/06：调度器延迟队列本身无界；业务入口必须自行准入，任务服务限制真实在途数。
     *
     * @return 任务线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor taskExecutor() {
        // 获取 cpu 核心数
        int cpu = Runtime.getRuntime().availableProcessors();

        // 创建 ScheduledThreadPoolExecutor，核心线程数为 cpu 核心数
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(cpu);

        // ScheduledThreadPoolExecutor 按核心线程运行，maximumPoolSize 不限制延迟队列。

        // 取消任务时会从队列中移除任务
        executor.setRemoveOnCancelPolicy(true);

        executor.setRejectedExecutionHandler((r, e) -> {
            // 保存被拒绝的任务
            // todo: 通知手动处理
            throw new RejectedExecutionException("任务执行线程池已满。");
        });

        return executor;
    }

    /**
     * 用于定时器的单线程 Scheduler。
     *
     * @return 定时器 Scheduler
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler timerScheduler() {
        // 创建一个单线程的 ThreadPoolTaskScheduler
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("timer-schedule-");
        return scheduler;
    }

    /**
     * 任务执行虚拟线程池，用于处理高并发吞吐的任务。
     * <br>建议不要使用 {@code synchronized} 而是使用 {@link java.util.concurrent.locks.ReentrantLock}
     *
     * @return 虚拟线程池执行器
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualExecutor() {
        // 使用虚拟线程执行器，每个任务都是独立虚拟线程
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    /**
     * 专门为 api 请求设计的 Scheduler
     *
     * @return 由 Spring 管理、供 API 工作使用的调度器
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler apiScheduler() {
        var availableProcessors = Runtime.getRuntime().availableProcessors();
        return Schedulers.newBoundedElastic(
                availableProcessors * 2,
                availableProcessors * 50,
                "api-scheduler"
        );
    }
}
