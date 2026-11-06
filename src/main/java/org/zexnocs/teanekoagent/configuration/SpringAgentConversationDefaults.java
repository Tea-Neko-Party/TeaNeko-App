package org.zexnocs.teanekoagent.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationSettings;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationDefaults;
import java.time.Duration;
import java.util.Objects;

/**
 * 从 Spring 内存环境懒读会话容量和空闲有效期，配置故障不阻断 Bean 创建。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.4-l
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
        } catch (RuntimeException failure) {
            throw new org.zexnocs.teanekocore.logger.DiagnosticException("Agent 会话保留配置不可用，请检查容量和空闲有效期。", failure);
        }
    }
}
