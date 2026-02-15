package org.zexnocs.teanekoapp.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.TaskRetryStrategy;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 异常任务测试
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@SpringBootTest
public class ExceptionTaskTest {
    @Autowired
    private ITaskService iTaskService;

    @Test
    void task_exception() {
        var currentTime = System.currentTimeMillis();
        var config = TaskConfig.<Void>builder()
                .name("测试任务 1")
                .callable(() -> {
                    System.out.println("执行任务，当前时间：" + (System.currentTimeMillis() - currentTime) + "ms");
                    return null;
                })
                .delayDuration(Duration.ofSeconds(0))
                .maxRetries(3)
                .retryStrategy(TaskRetryStrategy.ALWAYS_RETRY)
                .retryInterval(Duration.ofSeconds(2))
                .expirationDuration(Duration.ofSeconds(2))
                .build();

        // 理应每 4s 执行一次， 2s 过期 + 2s 重试间隔
        var future = iTaskService.subscribe(config, Void.class);
        assertThrows(RuntimeException.class, () -> future.finish().join());
    }
}
