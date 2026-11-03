package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.adapter.onebot.OnebotAgentTools;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupInfoSender;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * 通过普通能力包安装 OneBot 资料和工具，平台来源不放入 Agent 组件或全局工具目录。
 * <br>2026/11/02：将注解提供者注册为 Bean，原能力保留资料所有权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.0-l
 */
@Configuration(proxyBeanMethods = false)
public class OnebotAgentConfig {
    /**
     * 2026/11/02：公开具体注解提供者，避免元数据扫描需要构造业务服务。
     * @param sources 宿主来源租约
     * @param groups 平台单群读取
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return OneBot 注解工具提供者
     */
    @Bean
    public OnebotAgentTools onebotAgentTools(NativeCommandBridge sources, GetGroupInfoSender groups, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        return new OnebotAgentTools(sources, groups, mapper, executor);
    }

    /**
     * 2026/11/02：工具和 Prompt 继续归属原能力，不重复自动注册。
     * @param source 独立提供者
     * @return 拥有明确资料命名空间的能力包
     */
    @Bean
    public AgentFeature onebotAgentFeature(OnebotAgentTools source) {
        return new SourceAgentFeature("teaneko.agent", "onebot", true, source, source, Set.of(OnebotAgentTools.CONTEXT));
    }
}
