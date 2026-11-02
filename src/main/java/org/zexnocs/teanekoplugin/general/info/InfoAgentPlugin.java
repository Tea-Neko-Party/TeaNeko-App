package org.zexnocs.teanekoplugin.general.info;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekocore.plugin.*;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoService;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * 将既有用户资料服务接入 Agent 插件能力，默认安装只读工具，不自动读取或注入用户资料。
 * <br>2026/11/02：独立注册注解提供者，保留插件能力与停用边界。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Configuration(proxyBeanMethods = false)
public class InfoAgentPlugin {
    public static final String ID = "general.info";
    /** @return 依赖 Agent 宿主的资料工具插件声明 */
    @Bean public PluginModule infoAgentModule() { return () -> new PluginDescriptor(ID, "4.6.0-l", 1, Set.of("teaneko.agent")); }

    /**
     * 2026/11/02：提供具体 Bean 类型，供注解目录延迟发现。
     * @param info 原公开资料服务
     * @param messages 原留言板服务
     * @param sources 可信原生来源
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 用户资料注解提供者
     */
    @Bean public InfoAgentTools infoAgentTools(InfoService info, MessageBoardInfoService messages, NativeCommandBridge sources,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new InfoAgentTools(info, messages, sources, mapper, executor);
    }

    /**
     * 2026/11/02：保留原安装能力，使用公共注解方法解析器。
     * @param source 已注册的注解提供者
     * @return 默认安装的资料工具能力
     */
    @Bean public AgentFeature infoAgentFeature(InfoAgentTools source) {
        return new SourceAgentFeature(ID, "user-info", true, source, IAgentPromptSource.empty());
    }
}
