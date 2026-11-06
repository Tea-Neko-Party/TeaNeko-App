package org.zexnocs.teanekoagent.component.social;

import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

/**
 * 关系、心情及关怀防重状态的可替换事务端口。
 * <br>2026/11/06：原文不落入社交状态，证据只保存摘要及来源防重键。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
public interface ISocialStateStore {
    /**
     * @param reference 空间代次
     * @param user 真实用户
     * @param initial 初值
     * @return 当前状态，不创建关系
     */
    CompletionStage<SocialState> read(MemoryNamespaceReference reference, UUID user, int initial);
    /**
     * @param reference 原空间
     * @param user 原用户
     * @param initial 初值
     * @param event 持久防重事件，空时不建事件行
     * @param now 事件时刻
     * @param update 锁内纯状态转换，不允许网络或异步操作
     * @return 实际提交的状态；同事件重复时返回原现状
     */
    CompletionStage<SocialState> update(MemoryNamespaceReference reference, UUID user, int initial,
                                       String event, Instant now, UnaryOperator<SocialState> update);
}
