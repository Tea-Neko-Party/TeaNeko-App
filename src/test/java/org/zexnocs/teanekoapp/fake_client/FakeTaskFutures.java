package org.zexnocs.teanekoapp.fake_client;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;

/**
 * 为离线客户端构造具有明确结果的任务，不启动额外执行器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class FakeTaskFutures {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private FakeTaskFutures() {
    }

    /**
     * 构造已经获得明确结果的测试任务，不额外提交后台线程或安装终端日志。
     *
     * @param logger 项目日志接口
     * @param taskName 用于识别任务的诊断名称
     * @param result 已经形成的处理结果
     * @param <T> 本方法约定的泛型数据类型
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     */
    public static <T> TaskFuture<T> completed(ILogger logger, String taskName, T result) {
        var future = new TaskFuture<T>(logger, taskName, new CompletableFuture<>());
        future.complete(result);
        return future;
    }
}
