package org.zexnocs.teanekocore.plugin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

/**
 * 声明稳定宿主能力身份，业务插件通过注解提供自己的入口及贡献 Bean。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：声明插件总管理中的基础模块介绍及当前实现版本。
 * <br>2026/11/07：宿主身份从业务插件目录分离，不参与启停与持久化开关。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class PluginConfiguration {
    /**
     * 2026/11/07：注册 Core 宿主能力和资源归属身份。
     * @return Core 宿主能力
     */
    @Bean
    public HostCapability teaNekoCoreCapability() {
        return new HostCapability("teaneko.core", "4.9.0-l", Set.of());
    }
}
