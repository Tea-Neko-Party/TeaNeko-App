package org.zexnocs.teanekoplugin.agent.cognition.integration.client;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoclient.agent.response.AgentMemoryWriteResponse;
import org.zexnocs.teanekoclient.agent.service.AgentClientOperationContext;
import org.zexnocs.teanekoclient.agent.service.AgentOperationProtocol;
import org.zexnocs.teanekoclient.agent.service.IAgentClientOperation;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekoplugin.agent.cognition.CognitionContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAddIntent;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactValue;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 将明确记忆写入适配到官方内部客户端，业务约束和数据类型由认知插件拥有。
 * <br>2026/11/08：保持原操作标识、写入标签与真实回执，停用由通用操作目录拒绝。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
@PluginOwned("agent.cognition")
public final class MemoryWriteOperation implements IAgentClientOperation<AgentMemoryWriteResponse> {
    private final MemoryAccessService memories;

    /**
     * 借用认知插件的授权写入入口。
     * @param memories 真实记忆访问服务
     */
    public MemoryWriteOperation(MemoryAccessService memories) {
        this.memories = memories;
    }

    /** @return 沿用原客户端记忆写入协议。 */
    @Override
    public AgentOperationProtocol<AgentMemoryWriteResponse> protocol() {
        return AgentMemoryWriteResponse.PROTOCOL;
    }

    /**
     * 主语固定为实际用户，仅接受当前原文中的明确记住意图。
     * @param operation 宿主固定的调用与执行资格
     * @return 实际事实新增结果
     * @throws IllegalArgumentException 当缺少有效空间引用或明确记住原文时
     */
    @Override
    public CompletionStage<AgentMemoryWriteResponse> execute(AgentClientOperationContext operation) {
        operation.check();
        var reference = CognitionContext.reference(operation.reference());
        if (reference == null) {
            throw new IllegalArgumentException("当前聊天尚未启用记忆上下文。");
        }
        var context = new MemoryAccessContext(operation.id(), operation.actor(), reference);
        var intent = MemoryAddIntent.fromUserText(context, operation.input().text()).orElseThrow(
                () -> new IllegalArgumentException("手动记忆须由当前用户明确提供记住原文。"));
        var value = new MemoryFactValue(MemoryFactValue.Kind.FACT, intent.text(), List.of(), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
        operation.check();
        return memories.append(context, "internal-memory", intent, value, operation.control())
                .thenApply(result -> new AgentMemoryWriteResponse(result.status().name(), result.recordId()));
    }
}
