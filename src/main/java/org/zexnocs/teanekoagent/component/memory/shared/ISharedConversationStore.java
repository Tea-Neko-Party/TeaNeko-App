package org.zexnocs.teanekoagent.component.memory.shared;

import org.zexnocs.teanekoagent.component.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 同空间按真实参与者定位原始对话，返回值不代表调用方已获跨聊天访问权。
 * <br>2026/11/06：索引可重建，原消息和引用仍是权威来源。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
public interface ISharedConversationStore {
    /**
     * @param current 当前固定空间
     * @param user 真实用户
     * @param now 查询时间
     * @return 同空间其他聊天最近的有界消息
     */
    CompletionStage<List<Entry>> related(MemoryNamespaceReference current, UUID user, Instant now);

    /**
     * @param proofs 已经采用的原记录凭据
     * @param now 复核时间
     * @return 原文、代次和有效期仍一致时为真
     */
    CompletionStage<Boolean> verifyShared(List<Proof> proofs, Instant now);

    /**
     * 不含原文的记录校验凭据。
     * @param reference 原聊天及空间
     * @param sequence 原消息序号
     * @param fingerprint 完整消息摘要
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    record Proof(MemoryNamespaceReference reference, long sequence, String fingerprint) {}

    /**
     * 仍在保留期内的权威消息，发送回执由使用方继续检查。
     * @param proof 原记录凭据
     * @param message 原消息
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    record Entry(Proof proof, MemoryLayers.Message message) {}
}
