package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.memory.layered.LongMemories;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 表达观察的持久化与采用端口，与模型提取和人格配置解耦。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public interface IStyleObservationStore {
    /**
     * 只读取原人格的合格样本与既有模式，不创建模型任务。
     * @param batch 固定来源
     * @param basis 当前人格
     * @param fields 当前白名单
     * @param now 当前时间
     * @return 有界目标
     */
    CompletionStage<StyleObservations.Target> prepare(LongMemories.Batch batch, AgentPersonalitySnapshot basis, Set<String> fields, Instant now);
    /**
     * 在原长期提交的同一个 EasyData 事务内保存观察并渐进采用。
     * 调用者已核对原始消息、空间代次和租约；实现不得另开异步事务。
     * @param batch 原始证据
     * @param target 宿主固定目标
     * @param candidates 已核对的表达候选
     * @param now 提交时间
     * @param guard 当前执行与学习资格
     */
    void acceptInTransaction(LongMemories.Batch batch, StyleObservations.Target target, List<StyleObservations.Candidate> candidates, Instant now, Runnable guard);
    /**
     * 查询当前人格的有限观察摘要，不返回其他 scope 原话。
     * @param scope 当前区域
     * @param basis 实际人格
     * @param now 当前时刻
     * @return 最多十六项观察摘要
     */
    CompletionStage<List<StyleObservations.View>> inspect(AgentScope scope, AgentPersonalitySnapshot basis, Instant now);
}
