package org.zexnocs.teanekoplugin.agent.cognition.memory;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组合会话窗口、持久历史、分层记忆与画像；各项权限仍由实际记忆服务复核。
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "memory", order = 20, name = "记忆", description = "保留对话上下文，按参数采集历史、召回和整理记忆。")
@PluginOwned("agent.cognition")
public final class MemoryComponent extends DeclaredAgentComponent {
    private org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoplugin.agent.cognition.memory.shared.SharedMemoryPromptSource> shared;

    /**
     * 2026/11/07：合并跨聊天回忆，静态发现不解析数据库依赖。
     * @param shared 受认知插件管理的来源
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installShared(org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoplugin.agent.cognition.memory.shared.SharedMemoryPromptSource> shared) {
        this.shared = shared;
    }

    /** @return 2026/11/07：记忆组件拥有的跨聊天来源。 */
    @Override public Set<String> promptNames() {
        return Set.of("shared_conversation");
    }

    /** @return 2026/11/07：实际运行时由原服务复核跨聊天开关。 */
    @Override public org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource prompts() {
        return new org.zexnocs.teanekoagent.runtime.prompt.DeferredAgentPromptSource(() -> shared.getObject());
    }

    private final MemoryInputSystem input;
    private final MemoryOutputSystem output;
    /**
     * 创建独立记忆参数，不加载空间、不创建存储记录。
     * @param input 准入后的输入采集系统
     * @param output 交付前的历史提交系统
     */
    public MemoryComponent(MemoryInputSystem input, MemoryOutputSystem output) {
        super("memory", Map.of("historyMaxMessages", -1, "historyCaptureEnabled", true, "historyReadEnabled", true,
                        "autoSummaryEnabled", true, "memoryReadEnabled", true, "memoryAddEnabled", true,
                        "memoryLearningEnabled", true, "crossChatRecallEnabled", false), List.of(
                new Field("historyMaxMessages", "上下文消息数", "-1 继承系统窗口；0 不保留上下文。", "NUMBER"),
                new Field("historyCaptureEnabled", "保存历史", "记录当前聊天的真实消息。", "BOOLEAN"),
                new Field("historyReadEnabled", "查询历史", "允许读取已有历史。", "BOOLEAN"),
                new Field("autoSummaryEnabled", "自动摘要", "压缩已完成的上下文。", "BOOLEAN"),
                new Field("memoryReadEnabled", "召回记忆", "读取与注入当前绑定空间的记忆。", "BOOLEAN"),
                new Field("memoryAddEnabled", "明确记忆", "允许本人新增、纠正和遗忘。", "BOOLEAN"),
                new Field("memoryLearningEnabled", "自动学习", "从对话整理稳定事实与用户画像。", "BOOLEAN"),
                new Field("crossChatRecallEnabled", "跨聊天回忆", "仅关联同空间中当前用户参与的群聊，私聊原文不向其他聊天公开。", "BOOLEAN")));
        this.input = input;
        this.output = output;
    }

    /** @return 记忆组件实际装配的已有业务系统来源。 */
    @Override
    public Set<String> features() {
        return Set.of("agent.cognition:builtin-memory", "agent.cognition:long-memory", "agent.cognition:layered-memory", "agent.cognition:memory-tools");
    }

    /** @return 组件拥有的输入采集与最终历史提交系统。 */
    @Override
    public List<org.zexnocs.teanekoagent.plugin.IAgentFeature.SystemContribution> systems() {
        return List.of(new org.zexnocs.teanekoagent.plugin.IAgentFeature.SystemContribution(
                        org.zexnocs.teanekoagent.plugin.IAgentFeature.Phase.BEFORE_INTERACTION, input),
                new org.zexnocs.teanekoagent.plugin.IAgentFeature.SystemContribution(
                        org.zexnocs.teanekoagent.plugin.IAgentFeature.Phase.BEFORE_DELIVERY, output));
    }

    /**
     * 2026/11/07：在声明类型校验后检查本组件业务范围，不依赖宿主字段表。
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当参数类型或业务范围无效时
     */
    @Override
    public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        var limit = parameters.get("historyMaxMessages");
        if (limit != null && (!limit.canConvertToInt() || limit.intValue() < -1)) {
            throw new IllegalArgumentException("历史消息上限必须为非负整数，或使用 -1 继承本地值。");
        }
    }
}
