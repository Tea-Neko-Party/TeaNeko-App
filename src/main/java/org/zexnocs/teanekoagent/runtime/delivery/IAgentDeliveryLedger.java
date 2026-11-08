package org.zexnocs.teanekoagent.runtime.delivery;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 实际平台发送的可选回执端口，由业务提供者管理占用、持久化及完成语义。
 * <br>2026/11/07：发送宿主和表达插件不再直接依赖认知账本实现。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentDeliveryLedger {
    /**
     * 在真实发送边界记录通用图文段，禁止把模型生成视为平台交付。
     * @param reference 原固定上下文，无持久化要求时为空
     * @param actor 原真实身份
     * @param requestId 原请求标识
     * @param index 当前段号
     * @param total 总段数
     * @param part 实际发送内容
     * @param guardedSend 最终守卫和真实发送阶段
     * @return 真实平台阶段和账本提交的完成信号
     */
    CompletionStage<Void> send(@Nullable IAgentContextReference reference, AgentActorContext actor, UUID requestId,
            int index, int total, AgentReplyPlan.IPart part, Supplier<CompletionStage<Void>> guardedSend);

    /**
     * 记录扩展发送器提供的有界文字投影和实际内容摘要。
     * @param reference 原固定上下文，无持久化要求时为空
     * @param actor 原真实身份
     * @param requestId 原请求标识
     * @param index 当前段号
     * @param total 总段数
     * @param text 可公开历史投影
     * @param digest 完整发送内容摘要
     * @param guardedSend 最终守卫和真实发送阶段
     * @return 真实平台阶段和账本提交的完成信号
     */
    CompletionStage<Void> sendRecorded(@Nullable IAgentContextReference reference, AgentActorContext actor,
            UUID requestId, int index, int total, String text, String digest,
            Supplier<CompletionStage<Void>> guardedSend);
}
