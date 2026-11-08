package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证总管理静态说明不会因停用丢失，也不会在查询时重新发现。
 * <br>2026/11/07：覆盖失败重扫的原子发布和未知插件拒绝。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginContributionDirectoryTest {
    /** 停用后仍读取完整安装说明，频繁查询不调用来源。 */
    @Test
    void disabledPluginsKeepInstalledDescriptionsWithoutDiscovery() {
        var source = mock(IPluginContributionSource.class);
        var plugins = plugins();
        var item = new PluginContribution("sample.plugin", "tool", "sample.tool", "示例工具", "测试说明");
        when(source.contributions()).thenReturn(List.of(item));
        var directory = new PluginContributionDirectory(List.of(source), plugins);
        assertThrows(IllegalStateException.class, () -> directory.list("sample.plugin"));
        directory.init();
        clearInvocations(source);
        plugins.setEnabled("sample.plugin", false);
        assertEquals(List.of(item), directory.list("sample.plugin"));
        directory.init();
        assertEquals(List.of(item), directory.list("sample.plugin"));
        verifyNoInteractions(source);
    }

    /** 相同插件、类别与标识的冲突会拒绝整次重扫，旧目录继续可读。 */
    @Test
    void failedReloadKeepsLastCompleteSnapshot() {
        var source = mock(IPluginContributionSource.class);
        var item = new PluginContribution("sample.plugin", "component", "memory", "记忆", "测试说明");
        when(source.contributions()).thenReturn(List.of(item));
        var directory = new PluginContributionDirectory(List.of(source), plugins());
        directory.init();
        when(source.contributions()).thenReturn(List.of(item, item));
        assertThrows(IllegalArgumentException.class, directory::reload);
        assertEquals(List.of(item), directory.list("sample.plugin"));
    }

    /** 来源不能借总管理目录伪造没有安装的插件。 */
    @Test
    void rejectsUnknownOwnersBeforePublishing() {
        var source = mock(IPluginContributionSource.class);
        when(source.contributions()).thenReturn(List.of(
                new PluginContribution("unknown.plugin", "page", "workspace", "页面", "测试说明")));
        var directory = new PluginContributionDirectory(List.of(source), plugins());
        assertThrows(IllegalArgumentException.class, directory::init);
        assertThrows(IllegalStateException.class, () -> directory.list("unknown.plugin"));
    }

    /** @return 没有生命周期资源的隔离插件目录 */
    private static PluginRegistry plugins() {
        return new PluginRegistry(List.of(() -> new PluginDescriptor("sample.plugin", "1", 1, Set.of())));
    }
}
