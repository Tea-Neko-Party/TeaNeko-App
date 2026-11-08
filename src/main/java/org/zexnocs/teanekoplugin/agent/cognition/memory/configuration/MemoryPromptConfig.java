package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.AgentMemoryPromptSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.IMemoryRecallDefaults;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/**
 * 默认接入真实记忆资料能力，与自定义贡献共同组合，构造不执行记忆访问。
 * <br>2026/11/02：资料改为默认能力，不再用缺少总来源 Bean 作为装配条件。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class MemoryPromptConfig {
    /**
     * 组合原访问服务与本地预算，不创建实例、空间、线程池或后台任务。
     * @param access 可信记忆访问边界
     * @param policies 真实读取策略
     * @param defaults 原注入默认预算
     * @param mapper JSON 转换器
     * @param executor 借用 Core 业务执行器
     * @return 默认安装的记忆资料能力
     */
    @Bean
    public org.zexnocs.teanekoagent.plugin.IAgentFeature agentMemoryPromptSource(MemoryAccessService access, IMemoryAccessPolicy policies,
            IMemoryRecallDefaults defaults, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        // 2026/11/02：权限及来源复核继续由原适配器负责。
        return new org.zexnocs.teanekoagent.plugin.SourceAgentFeature("agent.cognition", "builtin-memory", true,
                (definition, request, runtime) -> java.util.List.of(),
                new AgentMemoryPromptSource(access, policies, defaults, mapper, executor, Clock.systemUTC()), java.util.Set.of("memory_profile", "memory_related"));
    }
}
