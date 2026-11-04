package org.zexnocs.teanekoagent.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataDeliveryStore;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataMemoryNamespaceStore;
import org.zexnocs.teanekoagent.component.memory.history.AgentDeliveryService;
import org.zexnocs.teanekoagent.component.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * 默认逐段发送账本，复用专用数据、业务执行器与历史保留期。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentDeliveryConfig {
    /**
     * 提供可替换的实际回执持久化。
     * @param data 专用入口
     * @param namespaces 原空间元数据
     * @param mapper 原转换器
     * @return 默认账本
     */
    @Bean @ConditionalOnMissingBean(IAgentDeliveryStore.class)
    public EasyDataDeliveryStore agentDeliveryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper) {
        return new EasyDataDeliveryStore(data, namespaces, mapper);
    }
    /**
     * 默认发送链在构造期不访问数据或平台。
     * @param store 原账本
     * @param executor 原业务域
     * @param environment 原历史保留策略
     * @return 发送观察边界
     */
    @Bean @ConditionalOnMissingBean(AgentDeliveryService.class)
    public AgentDeliveryService agentDeliveryService(IAgentDeliveryStore store, @Qualifier("virtualExecutor") ExecutorService executor, Environment environment) {
        return new AgentDeliveryService(store, executor, Clock.systemUTC(),
                () -> Duration.ofDays(environment.getProperty("tea-neko.agent.history.retention-days", Long.class, 7L)));
    }
}
