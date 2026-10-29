package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoagent.personality.IPersonalityStore;
import org.zexnocs.teanekoagent.personality.PersonalityService;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/**
 * 默认人格组合根，不创建实例、读取人格或执行补正学习。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
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
