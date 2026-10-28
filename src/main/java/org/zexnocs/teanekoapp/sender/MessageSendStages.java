package org.zexnocs.teanekoapp.sender;

import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 消息完成链的公共适配，不依赖客户端、日志或线程池。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MessageSendStages {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private MessageSendStages() {}

    /**
     * 等待发送回执；null 是旧发送适配器的失败值，不能转换成成功的 Void。
     *
     * @param task 用于本次处理的 {@code task} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static CompletionStage<Void> awaitResponse(TaskFuture<? extends IMessageSendResponseData> task) {
        return Objects.requireNonNull(task, "task").asStage().thenAccept(response -> {
            if (response == null) {
                throw new MessageSendFailedException();
            }
        });
    }

    /**
     * 冻结消息列表后依次发送分段，前一段成功结束才启动下一段。
     * 任一段异常即结束整个链，不发送余下分段，不回滚已经发送的内容。
     * sender 负责检查自己的发送回执；配置快照也由具体构造器捕获。
     *
     * @param messages 保持原始顺序的统一消息列表
     * @param partSize 每个分段包含的最大条目数
     * @param sender 用于本次处理的 {@code sender} 数据
     * @param <T> 本方法约定的泛型数据类型
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static <T> CompletionStage<Void> sendByPart(
            List<T> messages, int partSize, Function<List<T>, ? extends CompletionStage<?>> sender) {
        if (partSize <= 0) {
            throw new IllegalArgumentException("partSize must be greater than 0");
        }
        Objects.requireNonNull(sender, "sender");
        var snapshot = List.copyOf(messages);
        CompletionStage<Void> result = CompletableFuture.completedStage(null);
        for (int start = 0; start < snapshot.size();) {
            // 用减法计算，避免 start + partSize 溢出。
            int end = start + Math.min(partSize, snapshot.size() - start);
            var part = List.copyOf(snapshot.subList(start, end));
            result = result.thenCompose(ignored -> Objects.requireNonNull(sender.apply(part), "send stage")
                    .thenApply(response -> (Void) null));
            start = end;
        }
        return result;
    }
}
