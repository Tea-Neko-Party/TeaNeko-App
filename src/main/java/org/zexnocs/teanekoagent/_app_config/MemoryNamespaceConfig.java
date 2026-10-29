package org.zexnocs.teanekoagent._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.memory.namespace.*;
import java.time.Clock;

/**
 * 空间注册与绑定事务服务的组合根；不注册聊天管理入口，不在构造时访问数据或启用 Agent。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryNamespaceConfig {
    /**
     * 使用可替换事务端口装配可信宿主服务，构造不创建默认空间。
     * @param store 元数据事务存储
     * @param defaults 本地配额策略
     * @return 空间注册服务
     */
    @Bean public MemoryNamespaceRegistry memoryNamespaceRegistry(IMemoryNamespaceStore store, IMemoryNamespaceDefaults defaults) {
        return new MemoryNamespaceRegistry(store, defaults, Clock.systemUTC());
    }

    /**
     * 装配可信宿主的绑定事务服务，不注册指令或改写当前会话。
     * @param store 绑定事务端口
     * @param defaults 本地创建配额
     * @return 等待真实提交的绑定服务
     */
    @Bean public MemoryNamespaceBindings memoryNamespaceBindings(IMemoryNamespaceBindingStore store, IMemoryNamespaceDefaults defaults) {
        return new MemoryNamespaceBindings(store, defaults, Clock.systemUTC());
    }
}
