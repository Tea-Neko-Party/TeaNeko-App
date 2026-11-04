package org.zexnocs.teanekoagent.component.llm.framework.interaction;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationContext;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 每次实际模型准入前的宿主守卫；运行策略不属于可导出模型选项或组件。
 * 工具副作用通过工具绑定的守卫独立检查，不从模型参数推导授权。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface ILLMInteractionGuard {
    /**
     * 在阶段计时范围和生命周期锁外复核准入；禁止阻塞等待同一交互。
     * @param context 固定宿主绑定及当前模型阶段控制
     * @throws org.zexnocs.teanekoagent.component.llm.framework.LLMOperationRejectedException 当宿主拒绝继续时
     */
    void check(ToolInvocationContext context);

    /**
     * 等待动态准入完成；同步守卫通过默认实现兼容，异步实现不得阻塞等待数据库或同一交互。
     * @param context 当前模型阶段的可信绑定和控制
     * @return 准入完成信号；失败阻止模型预算预占和真实请求
     */
    default CompletionStage<Void> checkAsync(ToolInvocationContext context) {
        check(context); return CompletableFuture.completedStage(null);
    }

    /**
     * 将异步宿主检查接入统一阶段，旧端口若仅支持同步检查必须明确拒绝。
     * @param action 生命周期锁外发起的异步检查
     * @return 支持异步检查的守卫
     * @throws NullPointerException 当检查函数为空时
     */
    static ILLMInteractionGuard asynchronous(Function<ToolInvocationContext, CompletionStage<Void>> action) {
        Objects.requireNonNull(action, "异步模型准入检查不能为空。");
        return new ILLMInteractionGuard() {
            /**
             * 拒绝把异步检查退化成提前返回的同步允许。
             * @param context 当前可信上下文
             * @throws UnsupportedOperationException 当端口未等待异步守卫时
             */
            @Override public void check(ToolInvocationContext context) {
                throw new UnsupportedOperationException("当前守卫要求交互端口等待异步准入检查。");
            }

            /**
             * 发起宿主检查，不读取模型参数推导身份或权限。
             * @param context 当前阶段上下文
             * @return 宿主实际检查完成阶段
             * @throws NullPointerException 当宿主返回空阶段时
             */
            @Override public CompletionStage<Void> checkAsync(ToolInvocationContext context) {
                return Objects.requireNonNull(action.apply(context), "异步模型准入阶段不能为空。");
            }
        };
    }
}
