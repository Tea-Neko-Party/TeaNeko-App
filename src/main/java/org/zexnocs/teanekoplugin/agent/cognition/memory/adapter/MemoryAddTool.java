package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 明确记忆意图的独立工具入口，不从模型参数接受记忆原文。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentToolClass(value = "memory-tools", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class MemoryAddTool {
    private final AgentMemoryToolSource source;

    /**
     * 注入共享原文校验及写入逻辑。
     * @param source 可信记忆工具服务
     */
    public MemoryAddTool(AgentMemoryToolSource source) {
        this.source = source;
    }

    /**
     * 复核原用户是否明确提出记忆要求。
     * @param context 固定宿主上下文
     * @return 原意图和写入开关均有效时为真
     */
    public boolean canAdd(TeaNekoAgentToolContext context) {
        return source.canAdd(context);
    }

    /**
     * 复用原始意图和前置保存回执，避免同一请求重复新增。
     * @param kind 事实或偏好类别
     * @param context 原宿主上下文
     * @return 写入回执或固定领域错误
     */
    @TeaNekoAgentTool(value = AgentMemoryToolSource.ADD, condition = "canAdd")
    @Description("立即保存本轮用户明确要求记住的原文，归属于本人；只选择事实或偏好，不改写原文。默认保存到新长期记忆，用 memory_search、memory_user_profile 查询，用 memory_correct、memory_forget 维护；旧模块独立装配保留原入口。")
    public CompletionStage<String> add(
            @Description("FACT 为明确事实，PREFERENCE 为明确偏好。") MemoryFactValue.Kind kind,
            TeaNekoAgentToolContext context) {
        return source.add(kind, context);
    }
}
