package org.zexnocs.teanekoagent.adapter.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.AgentActivity;
import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.command.ICommandInvocationPort;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.AbstractLLMFunctionParameter;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 只向已授权且具有原生来源的请求暴露命令文本工具，不接受身份、空间或权限参数。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
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
        if (!definition.activities().contains(AgentActivity.TOOL) || !definition.activities().contains(AgentActivity.COMMAND)
                || request.memoryReference() == null || !commands.available(request)
                || !configs.getConfigData(manager, AgentScopeConfigData.class, request.actor().scope().scopeId())
                        .map(data -> data.toPolicy().enabled() && data.isCommandToolsEnabled()).orElse(false)) { return List.of(); }
        var tool = LLMTool.builder().name(NAME).toolPackage("agent.command")
                .description("在当前真实用户身份下调用原注册命令，保留原权限及原生反馈；完成仅表示处理链结束，不代表具体业务成功。状态不确定的动作不可自动重试。")
                .parameters(AbstractLLMFunctionParameter.object("命令业务参数，不接受身份或权限。",
                        Map.of("command", AbstractLLMFunctionParameter.primitive("string", "完整原生命令文本，最多4096字符；分词与普通文本命令相同。")), List.of("command"))).build();
        return List.of(new LLMToolBinding(tool, (call, context) -> {
            try {
                var args = mapper.readTree(call.getArguments());
                if (!args.isObject() || args.size() != 1 || !args.path("command").isString()) { throw new IllegalArgumentException("命令工具参数无效。"); }
                return commands.invoke(request, call.getId(), args.path("command").stringValue(), context).thenApply(receipt -> mapper.writeValueAsString(Map.of(
                        "status", receipt.status(), "feedbackOwner", "NATIVE_COMMAND", "description", "此回执只描述命令处理状态；具体业务反馈由原命令负责，未知副作用不得自动重试。")));
            } catch (RuntimeException invalid) { return CompletableFuture.completedStage("{\"status\":\"INVALID_ARGUMENTS\",\"description\":\"命令参数或可信来源无效。\"}"); }
        }));
    }
}
