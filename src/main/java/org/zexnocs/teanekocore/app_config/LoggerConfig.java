package org.zexnocs.teanekocore.app_config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekocore.logger.DefaultLogger;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 日志配置。
 * 如果没有服务实现了 ILogger 接口，那么使用默认的日志实现。
 */
@Configuration
public class LoggerConfig {
    @Bean
    @ConditionalOnMissingBean(ILogger.class)
    public ILogger logger() {
        return new DefaultLogger();
    }
}
