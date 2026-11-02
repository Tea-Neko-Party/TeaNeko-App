package org.zexnocs.teanekocore.plugin.operation;

import java.util.concurrent.CompletionStage;

/**
 * 具名异步操作的环绕扩展，返回值必须包含前置、下一节点及后置处理。
 * @param <I> 输入类型
 * @param <O> 结果类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@FunctionalInterface
public interface AsyncInterceptor<I, O> {
    /**
     * 环绕一次操作，下一节点至多调用一次。
     * @param input 原输入
     * @param next 下一节点
     * @return 包含全部业务的完成阶段
     */
    CompletionStage<O> intercept(I input, AsyncOperation<I, O> next);
}
