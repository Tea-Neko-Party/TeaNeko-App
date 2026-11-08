package org.zexnocs.teanekoplugin.general.info;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoService;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * 将既有用户资料服务接入 Agent 插件能力，默认安装只读工具，不自动读取或注入用户资料。
 * <br>2026/11/02：独立注册注解提供者，保留插件能力与停用边界。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：资料和留言工具独立注册，继续组合为原插件能力。
 * <br>2026/11/05：在宿主初始化或能力装配时编译工具目录，运行选择只读缓存。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@PluginOwned("general.info")
@TeaNekoPlugin(id = "general.info", name = "用户资料与留言", version = "4.9.0-l",
        description = "提供公开个人资料、留言板和只读 Agent 工具。", category = "通用",
        dependencies = {"teaneko.agent"}, switchMode = PluginSwitchMode.HOT)
public class InfoAgentPlugin {
    public static final String ID = "general.info";

    /**
     * 2026/11/02：提供具体 Bean 类型，供注解目录延迟发现。
     * @param info 原公开资料服务
     * @param messages 原留言板服务
     * @param sources 可信原生来源
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 用户资料注解提供者
     */
    @Bean public InfoAgentTools infoAgentTools(InfoService info, MessageBoardInfoService messages, NativeMessageSources sources,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new InfoAgentTools(info, messages, sources, mapper, executor);
    }

    /**
     * 2026/11/05：提供独立留言工具，复用共享业务服务。
     * @param source 资料及权限服务
     * @return 留言工具 Bean
     */
    @Bean
    public InfoUserMessagesTool infoUserMessagesTool(InfoAgentTools source) {
        return new InfoUserMessagesTool(source);
    }

    /**
     * 2026/11/05：保留原安装能力，组合真实 Bean 的独立注解入口。
     * @param source 已注册的注解提供者
     * @param messages 独立留言工具
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 默认安装的资料工具能力
     */
    @Bean
    public IAgentFeature infoAgentFeature(InfoAgentTools source, InfoUserMessagesTool messages, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        // 2026/11/05：在能力装配时编译实际工具，选择阶段不做发现。
        TeaNekoAgentToolMethods.prepare(messages);
        return new SourceAgentFeature(ID, "user-info", true, (definition, request, runtime) -> {
            var selected = new java.util.ArrayList<>(source.select(definition, request, runtime));
            selected.addAll(TeaNekoAgentToolMethods.select(messages, definition, request, runtime, null, mapper, executor));
            return java.util.List.copyOf(selected);
        }, IAgentPromptSource.empty());
    }
}
