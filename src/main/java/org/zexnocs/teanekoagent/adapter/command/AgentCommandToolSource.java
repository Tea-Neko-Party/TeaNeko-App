package org.zexnocs.teanekoagent.adapter.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.AgentActivity;
import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.command.ICommandInvocationPort;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.description.Description;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 只向已授权且具有原生来源的请求暴露命令文本工具，不接受身份、空间或权限参数。
 * <br>2026/11/02：命令参数改为注解签名，原生权限和反馈归属继续由原桥接负责。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Service
@TeaNekoAgentToolClass(value = "builtin-tools", toolPackage = "agent.command", managedByFeature = true)
public final class AgentCommandToolSource {
    public static final String NAME = "command_dispatch";
    private final ICommandInvocationPort commands;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final ObjectMapper mapper;
    /**
     * 借用原命令端口及配置，不扫描第二份命令目录或自动开放工具。
     * @param commands 原生命令桥接
     * @param configs 实际配置服务
     * @param manager Agent 注册声明
     * @param mapper JSON 转换器
     */
    public AgentCommandToolSource(ICommandInvocationPort commands, IConfigDataService configs, AgentScopeConfigManager manager, ObjectMapper mapper) {
        this.commands = commands; this.configs = configs; this.manager = manager;
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    /**
     * 在本轮明确能力、作用域开关及原生来源全部成立时选择工具。
     * @param definition 当前实例定义
     * @param request 当前已固定请求
     * @return 零或一个冻结命令工具
     */
    public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request) {
        // 2026/11/02：工具选择和自动发现共用注解定义。
        return TeaNekoAgentToolMethods.select(this, definition, request, null, null, mapper, Runnable::run);
    }

    /**
     * 根据当前实例能力、原生来源和配置检查准入，执行前后均复核。
     * @param context 固定宿主上下文
     * @return 允许原生命令工具时为真
     */
    public boolean canDispatch(TeaNekoAgentToolContext context) {
        var request = context.request();
        return context.definition().activities().contains(AgentActivity.COMMAND) && request.memoryReference() != null
                && commands.available(request)
                && configs.getConfigData(manager, AgentScopeConfigData.class, request.actor().scope().scopeId())
                        .map(data -> data.toPolicy().enabled() && data.isCommandToolsEnabled()).orElse(false);
    }

    /**
     * 使用原工具调用标识派发，状态未知时仍禁止自动重试。
     * @param command 完整原生命令文本
     * @param context 宿主注入的实际调用
     * @return 原命令处理回执
     */
    @TeaNekoAgentTool(value = NAME, condition = "canDispatch")
    @Description("在当前真实用户身份下调用原注册命令，保留原权限及原生反馈；完成仅表示处理链结束，不代表具体业务成功。状态不确定的动作不可自动重试。")
    public CompletionStage<String> dispatch(@Description("完整原生命令文本，最多4096字符；分词与普通文本命令相同。") String command,
            TeaNekoAgentToolContext context) {
        try {
            return commands.invoke(context.request(), context.requireCall().getId(), command, context.requireInvocation())
                    .thenApply(receipt -> mapper.writeValueAsString(Map.of("status", receipt.status(), "feedbackOwner", "NATIVE_COMMAND",
                            "description", "此回执只描述命令处理状态；具体业务反馈由原命令负责，未知副作用不得自动重试。")));
        } catch (RuntimeException invalid) {
            return CompletableFuture.completedStage("{\"status\":\"INVALID_ARGUMENTS\",\"description\":\"命令参数或可信来源无效。\"}");
        }
    }
}
