package org.zexnocs.teanekoagent.component.memory.adapter;

import org.zexnocs.teanekoagent.component.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 明确遗忘本人长期条目的独立工具入口。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentToolClass(value = "long-memory", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
public final class LongTermForgetTool {
    private final LongTermMemoryTools source;

    /**
     * 注入共享长期访问与版本仲裁逻辑。
     * @param source 共享长期工具服务
     */
    public LongTermForgetTool(LongTermMemoryTools source) {
        this.source = source;
    }

    /**
     * 复核原文明确遗忘意图和当前写入资格。
     * @param context 固定宿主上下文
     * @return 可以遗忘时为真
     */
    public boolean canForget(TeaNekoAgentToolContext context) {
        return source.canForget(context);
    }

    /**
     * 依据查询到的版本遗忘本人条目。
     * @param id 本人记忆 UUID
     * @param revision 原查询版本
     * @param context 原宿主上下文
     * @return 事务提交回执
     */
    @TeaNekoAgentTool(value = "memory_forget", condition = "canForget")
    @Description("仅在本轮用户原文以“忘记/请忘记/删除记忆/请删除记忆”明确要求时，按 UUID 和 revision 遗忘本人长期条目。先查询目标，含糊时澄清；不删除原聊天或他人记录，旧证据不能自动恢复该条目。")
    public CompletionStage<LongMemories.Item> forget(@Description("已查询到的本人记忆 UUID。") UUID id,
            @Description("查询结果中的原 revision。") long revision, TeaNekoAgentToolContext context) {
        return source.forget(id, revision, context);
    }
}
