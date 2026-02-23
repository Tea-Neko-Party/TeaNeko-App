package org.zexnocs.teanekocore.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskRetryStrategy;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.util.TestConsoleFormatter;

import java.time.Duration;

/**
 * 任务测试
 *
 * @author zExNocs
 * @date 2026/02/13
 */
@SpringBootTest
public class TaskTest {
    @Autowired
    private ITaskService iTaskService;

    /**
     * 测试一个正常的任务，任务将在 5 秒后执行，执行过程中会记录日志，并在完成后输出结果。
     */
    @Test
    void task_normal() {
        var formatter = TestConsoleFormatter.create()
                .startTest("task_normal")
                .step("初始化任务配置");
        long startTime = System.currentTimeMillis();
        var config = TaskConfig.<Boolean>builder()
                .name("测试任务 1")
                .callable(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    formatter.timeCost("任务执行成功", elapsed)
                            .step("处理任务结果")
                            .success("任务执行成功!")
                            .step("任务流程结束");
                    return new TaskResult<>(true, true);
                })
                .delayDuration(Duration.ofSeconds(5))
                .maxRetries(3)
                .retryStrategy(TaskRetryStrategy.ALWAYS_RETRY)
                .retryInterval(Duration.ofSeconds(2))
                .expirationDuration(Duration.ofSeconds(60))
                .build();
        formatter.taskConfig("测试任务 1", "5秒", "3次")
                .separator()
                .step("提交任务到服务");

        var future = iTaskService.subscribeWithFuture(config, Boolean.class);
        formatter.step("等待任务完成");
        future.thenAccept(result -> formatter.step("任务 future 链执行")
                .step("接收到结果" + result.getResult()));
        future.finish().join();
        formatter.endTest().print();
    }
}
