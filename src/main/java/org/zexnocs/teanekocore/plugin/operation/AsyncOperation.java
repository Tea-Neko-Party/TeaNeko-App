package org.zexnocs.teanekocore.plugin.operation;

import java.util.concurrent.CompletionStage;

/**
 * 类型化异步操作或下一节点，不携带同步任务链游标。
 * @param <I> 输入类型
 * @param <O> 结果类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@FunctionalInterface
public interface AsyncOperation<I, O> {
    /**
     * 执行并返回完整业务阶段。
     * @param input 本次输入
     * @return 真实完成阶段
     */
    CompletionStage<O> execute(I input);
}
