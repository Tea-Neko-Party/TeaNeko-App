package org.zexnocs.teanekoapp._app_config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Jpa Config 是 JPA 的配置类，启用 JPA 仓库和实体扫描。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Configuration
@EnableJpaRepositories(basePackages = "org.zexnocs.teanekocore")
@EntityScan(basePackages = "org.zexnocs.teanekocore")
public class JpaConfig {
}
