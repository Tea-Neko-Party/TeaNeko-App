package org.zexnocs.teanekoapp.plugin;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证客户端安装事实形成插件依赖，缺少客户端明确阻塞且循环发布保持旧完整目录。
 * <br>2026/11/07：不借用在线状态推断安装，不访问平台网络。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginClientDirectoryTest {
    /** 只有安装目录参与依赖判断，提供者停用会让消费者失效且恢复后旧令牌仍无效。 */
    @Test
    void providerEdgesAndMissingClientsUseInstalledFacts() {
        var registry = new PluginRegistry(List.of(
                () -> new PluginDescriptor("provider", "1", 1, Set.of()),
                () -> new PluginDescriptor("consumer", "1", 1, Set.of()),
                () -> new PluginDescriptor("missing", "1", 1, Set.of())));
        var scanner = mock(TeaNekoClientScanner.class);
        var installed = new TeaNekoClientScanner.Installed(Map.of("extension",
                IndependentPair.of(mock(TeaNekoClient.class), mock(ITeaNekoClient.class))),
                Map.of("extension", "provider"));
        when(scanner.installed()).thenReturn(installed);
        var directory = new PluginClientDirectory(scanner, registry, List.of(
                new PluginClientRequirement("consumer", Set.of("extension")),
                new PluginClientRequirement("missing", Set.of("absent"))));
        directory.refresh();
        assertEquals(Set.of("provider"), registry.dependencies("consumer"));
        assertTrue(registry.isEnabled("consumer"));
        assertFalse(registry.isEnabled("missing"));
        assertTrue(registry.blockers("missing").contains("缺少已注册客户端：absent"));
        var token = registry.capture("consumer");
        registry.setEnabled("provider", false);
        assertFalse(registry.isEnabled("consumer"));
        directory.refresh();
        assertTrue(registry.blockers("consumer").isEmpty());
        registry.setEnabled("provider", true);
        assertTrue(registry.isEnabled("consumer"));
        assertThrows(IllegalStateException.class, () -> registry.check(token));
        verify(scanner, never()).getHandlerMap();
    }

    /** 客户端提供关系产生依赖环时拒绝完整候选，不能发布部分可用结果。 */
    @Test
    void rejectsClientProviderCyclesWithoutChangingPublishedGraph() {
        var registry = new PluginRegistry(List.of(
                () -> new PluginDescriptor("provider", "1", 1, Set.of("consumer")),
                () -> new PluginDescriptor("consumer", "1", 1, Set.of())));
        var scanner = mock(TeaNekoClientScanner.class);
        when(scanner.installed()).thenReturn(new TeaNekoClientScanner.Installed(Map.of("extension",
                IndependentPair.of(mock(TeaNekoClient.class), mock(ITeaNekoClient.class))),
                Map.of("extension", "provider")));
        var directory = new PluginClientDirectory(scanner, registry,
                List.of(new PluginClientRequirement("consumer", Set.of("extension"))));
        long before = registry.revision();
        assertThrows(IllegalArgumentException.class, directory::refresh);
        assertEquals(before, registry.revision());
        assertEquals(Set.of(), registry.dependencies("consumer"));
        assertTrue(registry.isEnabled("provider"));
    }
}
