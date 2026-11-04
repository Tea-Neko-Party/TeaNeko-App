package org.zexnocs.teanekoagent.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.memory.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.skill.adapter.AgentSkills;
import org.zexnocs.teanekoagent.runtime.AgentService;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 装配独立 Agent 轮次，借用 Core 执行资源，不创建默认实例或启动后台业务。
 * <br>2026/11/02：组合内置和插件来源并安装受控 System、决策事件及异步拦截。
 * <br>2026/11/02：注解提供者只经其能力贡献一次，不作为宿主额外来源重复聚合。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentExecutionConfig {
    /**
     * 尚未显式配置工具能力时使用空工具集，不回退到全局全部工具。
     * @return 可由能力装配替换的无工具默认策略
     */
    private IAgentToolSource defaultTools() { return (definition, request, runtime) -> List.of(); }

    /**
     * 注入运行依赖并预编译管线，构造时不读取配置或执行就绪检查。
     * 返回具体实现以声明实际关闭方法，业务使用方仍按独立轮次接口注入。
     * @param gate 统一准入入口
     * @param interactions 完整 LLM 交互端口
     * @param tools 显式工具策略提供者，缺失时选择空工具策略，不依赖配置类扫描顺序
     * @param mapper JSON 校验器
     * @param executor Core 业务执行器
     * @param scheduler Core 计时器
     * @param logger 项目日志
     * @param references 当前持久化空间引用端口
     * @return 拥有本服务轮次、借用共享资源的异步执行服务
     */
    public AgentService agentTurnService(AgentAccessGate gate, ILLMInteractionService interactions, ObjectProvider<IAgentToolSource> tools,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, IAgentMemoryReferences references) {
        // 2026/11/02：注解工具由插件能力统一拥有，独立宿主仍可直接传入显式工具端口。
        return new AgentService(gate, interactions, extraTools(tools), mapper,
                executor, scheduler, Clock.systemUTC(), logger, references);
    }

    /**
     * Spring 组合可选资料端口，缺失时明确使用空策略，构造不读取资料或创建默认实例。
     * @param gate 统一准入入口
     * @param interactions 完整交互端口
     * @param tools 显式工具源提供者
     * @param mapper 协议转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param logger 项目日志
     * @param references 固定空间端口
     * @param prompts 可选本轮资料端口提供者
     * @param personalities 可选人格源，与默认邮箱共用同一实例
     * @param history 可选历史记录端口
     * @param summaries 可选摘要使用复核
     * @param skills 可选 Skill 组合能力，与原工具及资料共同装配
     * @param monitor 可选脱敏监控，故障不改变业务结果
     * @param plugins 插件能力装配与复核入口
     * @return 拥有本轮资源、借用共享执行器的服务
     */
    @Bean(name = "agentTurnService", destroyMethod = "close")
    public AgentService agentTurnServiceWithPrompt(AgentAccessGate gate, ILLMInteractionService interactions, ObjectProvider<IAgentToolSource> tools,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, IAgentMemoryReferences references,
            ObjectProvider<IAgentPromptSource> prompts, ObjectProvider<IAgentPersonalitySource> personalities, ObjectProvider<IAgentHistoryRecorder> history,
            ObjectProvider<IAgentConversationSummaries> summaries, ObjectProvider<AgentSkills> skills, ObjectProvider<AgentRuntimeMonitor> monitor,
            ObjectProvider<org.zexnocs.teanekoagent.plugin.AgentPluginRuntime> plugins) {
        // 2026/11/02：过滤同时实现旧来源接口的注解提供者，避免工具和参考资料重复。
        var selectedTools = extraTools(tools); var selectedPrompts = extraPrompts(prompts);
        // 2026/11/02：旧总来源只代表宿主附加来源，不再替换内置能力。
        var pluginRuntime = plugins.getIfAvailable();
        if (pluginRuntime != null) { selectedTools = pluginRuntime.tools(selectedTools); selectedPrompts = pluginRuntime.prompts(selectedPrompts); }
        var capability = skills.getIfAvailable();
        if (capability != null) { selectedTools = capability.tools(selectedTools); selectedPrompts = capability.prompts(selectedPrompts); }
        var service = new AgentService(gate, interactions, selectedTools, mapper, executor, scheduler,
                Clock.systemUTC(), logger, references, selectedPrompts, personalities.getIfAvailable(), history.getIfAvailable(), summaries.getIfAvailable());
        if (pluginRuntime != null) { service.installPlugins(pluginRuntime); }
        var observer = monitor.getIfAvailable(); if (observer != null) { service.observe(observer::accept); } return service;
    }

    /**
     * 取得明确的宿主附加工具来源，注解提供者由能力路径单独装配。
     * @param providers 候选工具端口
     * @return 唯一附加来源或空来源
     * @throws IllegalStateException 当存在多个未明确组合的附加来源时
     */
    private IAgentToolSource extraTools(ObjectProvider<IAgentToolSource> providers) {
        var values = providers.orderedStream().filter(value -> !org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolRegistry.isProvider(value)).toList();
        if (values.size() > 1) { throw new IllegalStateException("宿主附加工具来源必须明确组合为一个端口。"); }
        return values.isEmpty() ? defaultTools() : values.getFirst();
    }

    /**
     * 取得宿主附加资料，不重复处理注解能力已经拥有的平台资料。
     * @param providers 候选资料端口
     * @return 唯一附加来源或静态空来源
     * @throws IllegalStateException 当存在多个未明确组合的附加来源时
     */
    private IAgentPromptSource extraPrompts(ObjectProvider<IAgentPromptSource> providers) {
        var values = providers.orderedStream().filter(value -> !org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolRegistry.isProvider(value)).toList();
        if (values.size() > 1) { throw new IllegalStateException("宿主附加资料来源必须明确组合为一个端口。"); }
        return values.isEmpty() ? IAgentPromptSource.empty() : values.getFirst();
    }
}
