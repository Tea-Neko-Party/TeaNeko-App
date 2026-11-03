package org.zexnocs.teanekocore.plugin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

/**
 * 声明宿主基础模块，外部插件通过显式 Spring 配置提供自己的入口及贡献 Bean。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
@Configuration(proxyBeanMethods = false)
public class PluginConfiguration {
    /**
     * 注册 Core 宿主身份。
     * @return Core 模块
     */
    @Bean public PluginModule teaNekoCoreModule() {
        return () -> new PluginDescriptor("teaneko.core", "4.6.0-l", 1, Set.of(), true);
    }
}
