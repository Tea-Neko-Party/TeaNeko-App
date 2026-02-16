package org.zexnocs.teanekocore.app_config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 核心必须要求开启的配置类。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class CoreConfig {
}
