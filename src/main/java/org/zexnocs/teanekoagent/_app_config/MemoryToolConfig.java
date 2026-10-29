package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.memory.AgentMemoryToolSource;
import org.zexnocs.teanekoagent.agent.IAgentToolSource;
import org.zexnocs.teanekoagent.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallDefaults;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * 提供明确的默认记忆工具源，用户自定义工具策略可完整替换，不合并全局工具目录。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryToolConfig {
    /**
     * 构造当前记忆工具策略，不读取开关、注册空间或调用模型。
     * @param access 可信记忆访问边界
     * @param policies 真实记忆配置策略
     * @param defaults 读取默认预算
     * @param mapper 项目 JSON 转换器
     * @param executor Core 借用业务执行器
     * @return 可由宿主替换的明确工具源
     */
    @Bean @ConditionalOnMissingBean(IAgentToolSource.class)
    public AgentMemoryToolSource agentMemoryToolSource(MemoryAccessService access, IMemoryAccessPolicy policies, IMemoryRecallDefaults defaults,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new AgentMemoryToolSource(access, policies, defaults, mapper, executor);
    }
}
