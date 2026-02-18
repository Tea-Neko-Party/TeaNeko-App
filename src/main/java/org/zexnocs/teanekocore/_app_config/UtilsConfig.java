package org.zexnocs.teanekocore._app_config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekocore.logger.DefaultLogger;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 工具类配置。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Configuration
public class UtilsConfig {
    /**
     * 提供一个默认的 ILogger 实现，如果没有其他实现被定义的话。
     *
     * @return {@link ILogger }
     */
    @Bean
    @ConditionalOnMissingBean(ILogger.class)
    public ILogger logger() {
        return new DefaultLogger();
    }
}
