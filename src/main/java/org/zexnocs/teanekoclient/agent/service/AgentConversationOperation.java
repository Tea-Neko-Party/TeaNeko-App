package org.zexnocs.teanekoclient.agent.service;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentUserText;
import org.zexnocs.teanekoagent.runtime.IAgentFactory;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;
import org.zexnocs.teanekoagent.runtime.IAgentService;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan;
import org.zexnocs.teanekoclient.agent.response.AgentConversationResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 官方内部客户端的普通聊天操作，复用原邮箱、预算和原生来源租约。
 * <br>2026/11/07：宿主聊天独立于业务插件注册，保持原结果复核及实例所有权。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class AgentConversationOperation implements IAgentClientOperation<AgentConversationResponse>, AutoCloseable {
    private final IAgentService agents;
    private final IAgentFactory factory;
    private final IAgentRegistry registry;
    private final NativeMessageSources sources;
    private @Nullable AgentHandle handle;
    private boolean closed;

    /**
     * 借用会话运行设施，构造不创建实例或调用模型。
     * @param agents 会话邮箱
     * @param factory 实例工厂
     * @param registry 实例所有者
     * @param sources 原生来源租约
     */
    public AgentConversationOperation(IAgentService agents, IAgentFactory factory, IAgentRegistry registry,
            NativeMessageSources sources) {
        this.agents = agents;
        this.factory = factory;
        this.registry = registry;
        this.sources = sources;
    }

    /** @return 原普通聊天传输协议 */
    @Override
    public AgentOperationProtocol<AgentConversationResponse> protocol() {
        return AgentConversationResponse.PROTOCOL;
    }

    /**
     * 运行固定原生图文，租约保留到业务及结果复核真实完成。
     * @param context 真实来源和客户端拥有的执行控制
     * @return 正式响应，不发送外部平台消息
     */
    @Override
    public CompletionStage<AgentConversationResponse> execute(AgentClientOperationContext context) {
        context.check();
        var target = instance();
        var actor = context.actor();
        var input = context.input();
        var request = new AgentRunRequest(context.id(), actor, AgentActivity.COMMAND,
                List.of(AgentReplyPlan.instructions(), input.message()), null, context.reference(),
                context.inherited() ? null : new AgentUserText(actor.sourceId(), input.text(), input.occurredAt()));
        var lease = sources.open(request, context.source());
        try {
            context.check();
            var running = context.inherited() ? agents.run(target, request, context.control()) : agents.run(target, request);
            var cancellation = context.control().onTermination(ignored ->
                    agents.cancel(target, actor.scope(), context.id(), actor.userId().orElseThrow()));
            return running.thenCompose(result -> result.status() == AgentRunResult.Status.SUCCESS
                            ? agents.retry(target, request, context.control()) : CompletableFuture.completedStage(result))
                    .thenApply(AgentConversationResponse::from)
                    .whenComplete((result, failure) -> {
                        cancellation.close();
                        lease.close();
                    });
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }

    /**
     * 按需创建本客户端拥有的实例，复用作用域邮箱和组件资格。
     * @return 当前自有实例
     * @throws IllegalStateException 客户端已关闭时
     */
    private synchronized AgentHandle instance() {
        if (closed) {
            throw new IllegalStateException("内部 Agent 聊天入口已关闭。");
        }
        if (handle == null || handle.isClosed()) {
            handle = factory.create(new AgentDefinition("agent-internal", new LLMModelOptionsSnapshot(null),
                    Set.of(AgentActivity.COMMAND, AgentActivity.TOOL)));
        }
        return handle;
    }

    /** 释放本处理器拥有的实例，借用邮箱与执行器由宿主管理。 */
    @Override
    @PreDestroy
    public synchronized void close() {
        closed = true;
        if (handle != null) {
            registry.remove(handle.id());
            handle = null;
        }
    }
}
