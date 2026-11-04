package org.zexnocs.teanekoclient.onebot.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * OneBot 主动向工具装配贡献平台工具与资料，保留原能力标识及资料所有权。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public final class OnebotAgentFeature implements AgentFeature {
    private final IAgentPromptSource promptSource;
    private final List<Object> tools;
    private final ObjectMapper mapper;
    private final Executor executor;

    /**
     * 组合 Spring 管理的单工具对象，工具实例不由运行时手动创建。
     * @param support 平台共享查询及资料来源
     * @param messages 消息读取工具
     * @param senders 发送者资料工具
     * @param groups 群资料工具
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     */
    public OnebotAgentFeature(OnebotAgentSupport support, OnebotMessageTool messages,
            OnebotSenderInfoTool senders, OnebotGroupInfoTool groups, ObjectMapper mapper, Executor executor) {
        // 2026/11/05：仅能力持有资料端口，不将平台资料注册为宿主的额外全局来源。
        this.promptSource = new IAgentPromptSource() {
            /**
             * 解析当前平台资料。
             * @param definition 实例定义
             * @param request 原始请求
             * @param runtime 当前运行设置
             * @param control 轮次控制
             * @return 平台自有资料片段
             */
            @Override
            public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition,
                    AgentRunRequest request, AgentRuntimeSnapshot runtime, ExecutionControl control) {
                return support.resolve(definition, request, runtime, control);
            }

            /**
             * 再次确认资料来源租约。
             * @param request 原始请求
             * @param input 当前布局
             * @param control 轮次控制
             * @return 校验完成阶段
             */
            @Override
            public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input,
                    ExecutionControl control) {
                return support.verify(request, input, control);
            }

            /**
             * 重放前确认原资料仍可使用。
             * @param request 原始请求
             * @param input 原布局
             * @param control 重放控制
             * @return 来源可重放时为真
             */
            @Override
            public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input,
                    ExecutionControl control) {
                return support.verifyReplay(request, input, control);
            }
        };
        this.tools = List.of(messages, senders, groups);
        this.mapper = mapper;
        this.executor = executor;
    }

    /** @return 原能力的稳定插件归属 */
    @Override
    public String pluginId() {
        return "teaneko.agent";
    }

    /** @return 原能力标识 */
    @Override
    public String id() {
        return "onebot";
    }

    /** @return 保留旧实例默认能力行为 */
    @Override
    public boolean defaultEnabled() {
        return true;
    }

    /** @return 2026/11/05：仅在装配工具组件时自动贡献平台工具及关联资料 */
    @Override
    public Set<String> assemblyRequirements() {
        return Set.of("tool");
    }

    /** @return 由当前能力统一组合的独立工具 */
    @Override
    public IAgentToolSource tools() {
        return this::select;
    }

    /** @return 当前平台参考资料来源 */
    @Override
    public IAgentPromptSource prompts() {
        return promptSource;
    }

    /** @return 保留原资料片段所有权 */
    @Override
    public Set<String> promptNames() {
        return Set.of(OnebotAgentSupport.CONTEXT);
    }

    /**
     * 统一编译独立注解工具，客户端和来源条件继续由原编译器执行。
     * @param definition 当前实例定义
     * @param request 可信请求
     * @param runtime 已固定运行设置，独立宿主可不提供
     * @return 当前请求可使用的工具绑定
     */
    public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request,
            @Nullable AgentRuntimeSnapshot runtime) {
        return tools.stream().flatMap(tool -> TeaNekoAgentToolMethods.select(
                tool, definition, request, runtime, null, mapper, executor).stream()).toList();
    }
}
