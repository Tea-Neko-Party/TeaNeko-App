package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 明确纠正本人长期条目的独立工具入口。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentToolClass(value = "long-memory", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class LongTermCorrectTool {
    private final LongTermMemoryTools source;

    /**
     * 注入共享长期访问与版本仲裁逻辑。
     * @param source 共享长期工具服务
     */
    public LongTermCorrectTool(LongTermMemoryTools source) {
        this.source = source;
    }

    /**
     * 复核原文明确纠正意图和当前写入资格。
     * @param context 固定宿主上下文
     * @return 可以纠正时为真
     */
    public boolean canCorrect(TeaNekoAgentToolContext context) {
        return source.canCorrect(context);
    }

    /**
     * 按原版本提交用户明确要求的更正。
     * @param id 本人记忆 UUID
     * @param revision 原查询版本
     * @param text 更正正文
     * @param context 原宿主上下文
     * @return 事务提交回执
     */
    @TeaNekoAgentTool(value = "memory_correct", condition = "canCorrect")
    @Description("仅在本轮用户原文以“纠正/请纠正/更正/请更正”明确提出时，按 UUID 和 revision 修改本人长期记忆。正文最多512字，成功后保护人工更正，不授予修改他人记录的权限。")
    public CompletionStage<LongMemories.Item> correct(@Description("已查询到的本人记忆 UUID。") UUID id,
            @Description("原 revision，版本变化时先重新查询。") long revision,
            @Description("用户要求的更正正文，最多512字符。") String text, TeaNekoAgentToolContext context) {
        return source.correct(id, revision, text, context);
    }
}
