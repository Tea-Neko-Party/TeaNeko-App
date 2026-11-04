package org.zexnocs.teanekoagent.component.personality.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoagent.component.personality.IPersonalityStore;
import org.zexnocs.teanekoagent.component.personality.PersonalityService;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/**
 * 默认人格组合根，不创建实例、读取人格或执行补正学习。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Configuration(proxyBeanMethods = false)
public class PersonalityConfig {
    /**
     * 默认接入配置与持久化生命周期，自定义人格端口可完整替换。
     * @param configuration 人格配置端口
     * @param store 人格事务端口
     * @param executor 借用 Core 执行器
     * @param mapper JSON 转换器
     * @return 实际人格服务
     */
    @Bean @ConditionalOnMissingBean(IAgentPersonalitySource.class)
    public PersonalityService personalityService(IPersonalityConfiguration configuration, IPersonalityStore store,
                                                 @Qualifier("virtualExecutor") ExecutorService executor, ObjectMapper mapper) {
        return new PersonalityService(configuration, store, executor, Clock.systemUTC(), mapper);
    }
}
