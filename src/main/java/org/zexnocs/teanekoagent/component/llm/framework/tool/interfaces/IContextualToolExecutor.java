package org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationContext;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 异步工具端口。调用参数不含宿主身份；资源适配器须响应 context.getControl() 的终止。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IContextualToolExecutor {
    /**
     * 在独立可信上下文上执行模型提供的业务参数，返回真实业务完成阶段；资源适配器应响应控制终止。
     *
     * @param call 模型产生的工具调用，身份不从参数中取得
     * @param context 用于本次处理的 {@code context} 数据
     * @return 等待工具业务结束后形成文本结果的阶段，异步失败继续传播
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    CompletionStage<String> call(ILLMToolCall call, ToolInvocationContext context) throws Exception;

    /**
     * 兼容短小的同步工具，在调用线程执行；不为阻塞 I/O 创建隐含线程池。
     *
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param executor 本地实际执行器
     * @return 满足本方法约束的 {@code IContextualToolExecutor} 结果
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    static IContextualToolExecutor synchronous(ILLMToolExecutor executor) {
        Objects.requireNonNull(executor, "executor");
        return (call, context) -> {
            context.getControl().checkActive();
            return CompletableFuture.completedFuture(executor.call(call.getArguments()));
        };
    }
}
