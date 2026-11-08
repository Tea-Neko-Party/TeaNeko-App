package org.zexnocs.teanekocore.plugin.operation;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import java.util.concurrent.CompletionStage;

/**
 * 类型化异步操作或下一节点，不携带同步任务链游标。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param <I> 输入类型
 * @param <O> 结果类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
@FunctionalInterface
public interface IAsyncOperation<I, O> {
    /**
     * 执行并返回完整业务阶段。
     * @param input 本次输入
     * @return 真实完成阶段
     */
    CompletionStage<O> execute(I input);
}
