package org.zexnocs.teanekoplugin.onebot.common.agent;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
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
 * <br>2026/11/05：在宿主初始化或能力装配时编译工具目录，运行选择只读缓存。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@PluginOwned("onebot.common")
public final class OnebotAgentFeature implements IAgentFeature {
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
        // 2026/11/05：在能力装配时编译实际工具，选择阶段不做发现。
        List.of(messages, senders, groups).forEach(TeaNekoAgentToolMethods::prepare);
        this.tools = List.of(messages, senders, groups);
        this.mapper = mapper;
        this.executor = executor;
    }

    /** @return 2026/11/07：随 OneBot 常用业务插件启停的能力归属 */
    @Override
    public String pluginId() {
        return "onebot.common";
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
