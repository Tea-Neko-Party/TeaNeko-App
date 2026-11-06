package org.zexnocs.teanekoagent.component.memory;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组合会话窗口、持久历史、分层记忆与画像；各项权限仍由实际记忆服务复核。
 *
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.4-l
 */
@TeaNekoAgentComponent(id = "memory", order = 20, name = "记忆", description = "保留对话上下文，按参数采集历史、召回和整理记忆。")
@PluginOwned("teaneko.agent")
public final class MemoryComponent extends BuiltinAgentComponent {
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
                        "memoryLearningEnabled", true), List.of(
                new Field("historyMaxMessages", "上下文消息数", "-1 继承系统窗口；0 不保留上下文。", "NUMBER"),
                new Field("historyCaptureEnabled", "保存历史", "记录当前聊天的真实消息。", "BOOLEAN"),
                new Field("historyReadEnabled", "查询历史", "允许读取已有历史。", "BOOLEAN"),
                new Field("autoSummaryEnabled", "自动摘要", "压缩已完成的上下文。", "BOOLEAN"),
                new Field("memoryReadEnabled", "召回记忆", "读取与注入当前绑定空间的记忆。", "BOOLEAN"),
                new Field("memoryAddEnabled", "明确记忆", "允许本人新增、纠正和遗忘。", "BOOLEAN"),
                new Field("memoryLearningEnabled", "自动学习", "从对话整理稳定事实与用户画像。", "BOOLEAN")));
        this.input = input;
        this.output = output;
    }

    /** @return 记忆组件实际装配的已有业务系统来源。 */
    @Override
    public Set<String> features() {
        return Set.of("teaneko.agent:builtin-memory", "teaneko.agent:long-memory", "teaneko.agent:layered-memory");
    }

    /** @return 组件拥有的输入采集与最终历史提交系统。 */
    @Override
    public List<org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution> systems() {
        return List.of(new org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution(
                        org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.BEFORE_INTERACTION, input),
                new org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution(
                        org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.BEFORE_DELIVERY, output));
    }
}
