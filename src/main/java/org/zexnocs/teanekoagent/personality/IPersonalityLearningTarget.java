package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 人格候选的可替换业务端口，与只读人格源及模型生成策略分开。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IPersonalityLearningTarget {
    /**
     * 复核原依据并取得代码和配置共同允许的字段，禁用学习时返回空集合。
     * @param scope 真实作用域
     * @param basis 互动实际采用的人格
     * @param control 借用控制
     * @return 本次允许的字段快照
     */
    CompletionStage<Set<String>> learningFields(AgentScope scope, AgentPersonalitySnapshot basis, ExecutionControl control);
    /**
     * 在原绑定和字段边界内提交候选，完成表示已观察持久化结果。
     * @param scope 原作用域
     * @param basis 生成时依据
     * @param candidate 宿主固定来源的候选
     * @param control 借用控制
     * @return 新增时为 true，原候选重复为 false
     */
    CompletionStage<Boolean> propose(AgentScope scope, AgentPersonalitySnapshot basis, PersonalityCorrection candidate, ExecutionControl control);
}
