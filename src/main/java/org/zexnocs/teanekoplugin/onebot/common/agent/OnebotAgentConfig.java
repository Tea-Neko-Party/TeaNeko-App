package org.zexnocs.teanekoplugin.onebot.common.agent;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupInfoSender;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutorService;

/**
 * 通过普通能力包安装 OneBot 资料和工具，平台来源不放入 Agent 组件或全局工具目录。
 * <br>2026/11/02：将注解提供者注册为 Bean，原能力保留资料所有权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：装配独立工具 Bean 与共享平台服务，客户端主动声明工具组件要求。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@PluginOwned("onebot.common")
public class OnebotAgentConfig {
    /**
     * 2026/11/02：公开具体注解提供者，避免元数据扫描需要构造业务服务。
     * @param sources 宿主来源租约
     * @param groups 平台单群读取
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return OneBot 共享平台支持
     */
    @Bean
    public OnebotAgentSupport onebotAgentTools(NativeMessageSources sources, GetGroupInfoSender groups, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        // 2026/11/05：保留原 Bean 名，只将多个工具注解移到独立类型。
        return new OnebotAgentSupport(sources, groups, mapper, executor);
    }

    /**
     * 2026/11/02：工具和 Prompt 继续归属原能力，不重复自动注册。
     * @param source 独立提供者
     * @param messages 消息工具
     * @param senders 发送者工具
     * @param groups 群资料工具
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 拥有明确资料命名空间的能力包
     */
    @Bean
    public IAgentFeature onebotAgentFeature(OnebotAgentSupport source, OnebotMessageTool messages,
            OnebotSenderInfoTool senders, OnebotGroupInfoTool groups, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        // 2026/11/05：能力组合现有 Spring 工具对象，不要求 Agent 层知道平台能力标识。
        return new OnebotAgentFeature(source, messages, senders, groups, mapper, executor);
    }
}
