package org.zexnocs.teanekoagent._app_config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.conversation.AgentQueueSettings;
import org.zexnocs.teanekoagent.agent.conversation.IAgentQueueDefaults;
import java.time.Duration;
import java.util.Objects;

/**
 * 从 Spring 本地环境按入队读取策略，构造时不解析配置，也不依赖 Agent 模型设置。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class SpringAgentQueueDefaults implements IAgentQueueDefaults {
    private final Environment environment;

    /**
     * 保存本地配置来源，不在启动阶段解析可能无效的设置。
     * @param environment Spring 已装载的内存环境
     */
    public SpringAgentQueueDefaults(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "排队配置环境不能为空。");
    }

    /**
     * 读取本次策略；失败不导出属性原值或原始异常链。
     * @return 本次固定的排队策略
     * @throws IllegalStateException 当属性不可解析或不满足范围时
     */
    @Override public AgentQueueSettings resolve() {
        try {
            return new AgentQueueSettings(environment.getProperty("tea-neko.agent.queue.max-waiting-requests", Integer.class, 5),
                    Duration.ofSeconds(environment.getProperty("tea-neko.agent.queue.timeout-seconds", Long.class, 30L)));
        } catch (RuntimeException ignored) {
            throw new IllegalStateException("Agent 排队配置不可用，请检查等待数量和时限。");
        }
    }
}
