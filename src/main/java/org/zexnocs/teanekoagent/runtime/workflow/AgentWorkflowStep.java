package org.zexnocs.teanekoagent.runtime.workflow;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.runtime.AgentHandle;

import java.util.Objects;

/**
 * 可信宿主显式安排的协作步骤，借用实例，不作为组件、持久化 DTO 或模型参数。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：提供明确的借用资源 getter，保留旧访问器和原拥有者的关闭责任。
 * @param name 有界步骤名称
 * @param agent 借用的独立实例，由调用方拥有和关闭
 * @param instruction 本步骤目标，按用户数据交接，不提升为运行规则
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@JsonIgnoreType
public record AgentWorkflowStep(String name, AgentHandle agent, String instruction) {
    /**
     * 拒绝无界目标和错误名称，不在构造时执行任何轮次。
     * @param name 步骤名称
     * @param agent 借用实例
     * @param instruction 宿主目标
     * @throws IllegalArgumentException 当名称或目标无效时
     */
    public AgentWorkflowStep {
        Objects.requireNonNull(agent, "协作实例不能为空。");
        if (name == null || !name.matches("[a-z][a-z0-9_-]{0,47}") || instruction == null || instruction.isBlank() || instruction.length() > 4096) {
            throw new IllegalArgumentException("协作步骤名称或目标无效。");
        }
    }

    /**
     * 2026/11/05：步骤仅取得宿主已登记实例，不负责创建或关闭该实例。
     * @return 由调用方拥有的同一实例
     */
    public AgentHandle getAgent() {
        return agent;
    }
}
