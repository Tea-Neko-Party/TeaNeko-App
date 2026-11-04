package org.zexnocs.teanekoagent.component.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 人格候选的可替换业务端口，与只读人格源及模型生成策略分开。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
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
