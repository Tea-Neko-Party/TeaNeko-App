package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.component.memory.configuration.MemoryNamespaceConfig;
import org.zexnocs.teanekoagent.component.memory.configuration.SpringMemoryNamespaceDefaults;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证配额配置与组合根懒加载、错误隐藏和下一次调用恢复。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class MemoryNamespaceDefaultsTest {
    /** 验证缺省五个及热更新正值，不在构造时解析无效属性。 */
    @Test void defaultsAreLazyAndReloadOnNextCall() {
        var environment = new MockEnvironment().withProperty("tea-neko.agent.memory.max-created-namespaces", "无效初值");
        var defaults = new SpringMemoryNamespaceDefaults(environment);
        environment.setProperty("tea-neko.agent.memory.max-created-namespaces", "5"); assertEquals(5, defaults.maxCreated());
        environment.setProperty("tea-neko.agent.memory.max-created-namespaces", "8"); assertEquals(8, defaults.maxCreated());
        environment.getPropertySources().remove("mockProperties"); assertEquals(5, defaults.maxCreated());
    }

    /** 验证非法配置只暴露固定中文原因，修正后恢复，不回传原始属性内容。 */
    @Test void invalidQuotaIsRedactedAndRecoverable() {
        var environment = new MockEnvironment(); var defaults = new SpringMemoryNamespaceDefaults(environment);
        for (var value : new String[]{"0", "-1", "敏感配置内容", "99999999999999"}) {
            environment.setProperty("tea-neko.agent.memory.max-created-namespaces", value);
            var error = assertThrows(IllegalStateException.class, defaults::maxCreated); assertNull(error.getCause());
            assertEquals("个人记忆空间配额配置无效。", error.getMessage());
        }
        environment.setProperty("tea-neko.agent.memory.max-created-namespaces", "1"); assertEquals(1, defaults.maxCreated());
    }

    /** 验证 Spring 组合根只保存端口，不因创建 Bean 读取配置或数据库。 */
    @Test void compositionRootDoesNotReadOrInitializeNamespaces() {
        var store = mock(IMemoryNamespaceStore.class); var defaults = mock(IMemoryNamespaceDefaults.class);
        assertNotNull(new MemoryNamespaceConfig().memoryNamespaceRegistry(store, defaults)); verifyNoInteractions(store, defaults);
    }
}
