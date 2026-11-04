package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;

import java.util.Objects;
import java.util.Set;

/**
 * 可共享的只读 Agent 定义，不包含实例会话、工具执行器或作用域授权。
 * <br>2026/11/02：允许显式选择插件能力，旧构造沿用宿主默认能力。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：增加独立装配快照，保留历史构造和共享定义。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param id 稳定定义标识
 * @param modelOptions 宿主选择的模型覆盖快照，默认路由仍由本地配置解析
 * @param activities 本定义允许参与的活动
 * @param features 显式安装的插件能力，使用插件ID:局部ID
 * @param assembly 当前聊天固定的无秘密组件装配
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentDefinition(String id, LLMModelOptionsSnapshot modelOptions, Set<AgentActivity> activities,
                              Set<String> features, AgentAssembly assembly) {
    /**
     * 2026/11/04：旧宿主的显式能力声明保持原默认行为。
     * @param id 定义标识
     * @param modelOptions 模型参数
     * @param activities 活动范围
     * @param features 原能力选择
     */
    public AgentDefinition(String id, LLMModelOptionsSnapshot modelOptions, Set<AgentActivity> activities, Set<String> features) {
        this(id, modelOptions, activities, features, AgentAssembly.legacyDefaults());
    }

    /**
     * @param selected 当前聊天独立装配
     * @return 2026/11/04：保持共享定义不变的本轮定义
     */
    public AgentDefinition withAssembly(AgentAssembly selected) {
        return new AgentDefinition(id, modelOptions, activities, features, selected);
    }

    /**
     * 2026/11/02：保留原定义构造，不隐式安装第三方可选能力。
     * @param id 定义标识
     * @param modelOptions 模型参数
     * @param activities 允许活动
     */
    public AgentDefinition(String id, LLMModelOptionsSnapshot modelOptions, Set<AgentActivity> activities) {
        this(id, modelOptions, activities, Set.of());
    }

    /**
     * 固定定义和能力集合；创建定义不会加载配置、启动模型或授予 scope 权限。
     * @param id 定义标识
     * @param modelOptions 模型参数快照
     * @param activities 允许的活动集合
     * @param features 显式能力集合
     * @param assembly 当前聊天固定的组件装配
     */
    public AgentDefinition {
        // 2026/11/04：共享定义与轮次装配分离，实例不接收缺失的装配快照。
        Objects.requireNonNull(assembly, "Agent 组件装配不能为空。");
        AgentScope.requireIdentity(id);
        Objects.requireNonNull(modelOptions, "模型参数快照不能为空。");
        activities = Set.copyOf(activities);
        // 2026/11/02：能力名称属于装配声明，不表示 scope 已获授权。
        features = Set.copyOf(features);
    }
}
