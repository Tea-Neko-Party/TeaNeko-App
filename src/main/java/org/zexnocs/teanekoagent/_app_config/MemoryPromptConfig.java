package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.memory.AgentMemoryPromptSource;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallDefaults;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/**
 * 默认接入真实记忆资料源，自定义 Prompt 源可完整替换，构造不执行记忆访问。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryPromptConfig {
    /**
     * 组合原访问服务与本地预算，不创建实例、空间、线程池或后台任务。
     * @param access 可信记忆访问边界
     * @param policies 真实读取策略
     * @param defaults 原注入默认预算
     * @param mapper JSON 转换器
     * @param executor 借用 Core 业务执行器
     * @return 可替换的实际记忆 Prompt 源
     */
    @Bean @ConditionalOnMissingBean(IAgentPromptSource.class)
    public AgentMemoryPromptSource agentMemoryPromptSource(MemoryAccessService access, IMemoryAccessPolicy policies,
            IMemoryRecallDefaults defaults, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new AgentMemoryPromptSource(access, policies, defaults, mapper, executor, Clock.systemUTC());
    }
}
