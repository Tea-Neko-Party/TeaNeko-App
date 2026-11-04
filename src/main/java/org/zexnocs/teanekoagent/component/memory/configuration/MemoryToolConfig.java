package org.zexnocs.teanekoagent.component.memory.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.component.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.component.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.component.memory.adapter.AgentMemoryToolSource;
import org.zexnocs.teanekoagent.component.memory.adapter.MemoryAddTool;
import org.zexnocs.teanekoagent.component.memory.adapter.MemoryProfileTool;
import org.zexnocs.teanekoagent.component.memory.history.adapter.AgentHistoryToolSource;
import org.zexnocs.teanekoagent.component.memory.recall.IMemoryRecallDefaults;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.integration.command.AgentCommandToolSource;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * 提供内置记忆、历史和命令工具能力，与第三方贡献共同组合。
 * <br>2026/11/02：内置工具改为普通能力贡献，不再因新增总来源 Bean 退让。
 * <br>2026/11/02：注解工具提供者注册为独立 Bean，保留原能力 ID。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：组合独立工具 Bean，保留内置能力标识与共享访问服务。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryToolConfig {
    /**
     * 2026/11/02：公开具体提供者类型供注解扫描，扫描期不读取业务数据。
     * @param access 可信记忆访问边界
     * @param policies 当前配置
     * @param defaults 读取预算
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 注解工具提供者
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public AgentMemoryToolSource agentMemoryTools(MemoryAccessService access, IMemoryAccessPolicy policies, IMemoryRecallDefaults defaults,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new AgentMemoryToolSource(access, policies, defaults, mapper, executor);
    }

    /**
     * 2026/11/05：注册独立画像工具，供扫描与能力选择共用。
     * @param source 共享记忆服务
     * @return 画像工具 Bean
     */
    @Bean
    public MemoryProfileTool memoryProfileTool(AgentMemoryToolSource source) {
        return new MemoryProfileTool(source);
    }

    /**
     * 2026/11/05：注册独立新增工具，保持原文凭据边界。
     * @param source 共享记忆服务
     * @return 新增工具 Bean
     */
    @Bean
    public MemoryAddTool memoryAddTool(AgentMemoryToolSource source) {
        return new MemoryAddTool(source);
    }

    /**
     * 2026/11/05：组合各独立工具 Bean，原组合能力与安装配置保持一致。
     * @param memory 记忆工具提供者
     * @param profile 独立画像工具
     * @param add 独立新增工具
     * @param history 可选历史工具
     * @param commands 可选命令工具
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 默认安装的内置工具能力
     */
    @Bean
    public org.zexnocs.teanekoagent.plugin.AgentFeature agentMemoryToolSource(
            AgentMemoryToolSource memory, MemoryProfileTool profile, MemoryAddTool add,
            ObjectProvider<AgentHistoryToolSource> history, ObjectProvider<AgentCommandToolSource> commands,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        var historyTools = history.getIfAvailable();
        var commandTools = commands.getIfAvailable();
        // 2026/11/02：原权限策略原样保留，仅改变装配身份。
        IAgentToolSource source = (definition, request, runtime) -> {
            var selected = new java.util.ArrayList<>(memory.select(definition, request, runtime));
            // 2026/11/05：选择实际 Bean 的注解入口，不在轮次中手动构造工具。
            selected.addAll(TeaNekoAgentToolMethods.select(profile, definition, request, runtime, null, mapper, executor));
            selected.addAll(TeaNekoAgentToolMethods.select(add, definition, request, runtime, null, mapper, executor));
            if (historyTools != null) {
                selected.addAll(historyTools.select(definition, request));
            }
            if (commandTools != null) {
                selected.addAll(commandTools.select(definition, request));
            }
            return java.util.List.copyOf(selected);
        };
        return new org.zexnocs.teanekoagent.plugin.SourceAgentFeature("teaneko.agent", "builtin-tools", true, source,
                org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty());
    }
}
