package org.zexnocs.teanekoagent._app_config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationSettings;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationDefaults;
import java.time.Duration;
import java.util.Objects;

/**
 * 从 Spring 内存环境懒读会话容量和空闲有效期，配置故障不阻断 Bean 创建。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class SpringAgentConversationDefaults implements IAgentConversationDefaults {
    private final Environment environment;

    /**
     * 保存本地配置来源，不解析属性或创建后台清理任务。
     * @param environment 已装载的 Spring 环境
     * @throws NullPointerException 当配置来源为空时
     */
    public SpringAgentConversationDefaults(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "会话配置环境不能为空。");
    }

    /**
     * 读取已校验的本次策略，失败时不携带属性原值或原始异常链。
     * @return 本地会话保留策略
     * @throws IllegalStateException 当属性不可解析或越界时
     */
    @Override public AgentConversationSettings resolve() {
        try {
            return new AgentConversationSettings(
                    environment.getProperty("tea-neko.agent.conversation.max-conversations", Integer.class, 1000),
                    Duration.ofSeconds(environment.getProperty("tea-neko.agent.conversation.idle-ttl-seconds", Long.class, 1800L)));
        } catch (RuntimeException ignored) {
            throw new IllegalStateException("Agent 会话保留配置不可用，请检查容量和空闲有效期。");
        }
    }
}
