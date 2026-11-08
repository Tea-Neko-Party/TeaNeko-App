package org.zexnocs.teanekoplugin.support;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.plugin.PluginContribution;
import org.zexnocs.teanekoapp.plugin.PluginContributionDirectory;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 停用插件的静态后台任务说明与查询缓存回归。
 * <br>2026/11/07：任务尚未实例化时总管理仍显示用途，普通查询不重复扫描。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginTimerContributionSourceTest {
    /** 静态目录保留停用任务，方法声明能够覆盖类型归属并过滤隐藏项。 */
    @Test
    void disabledPluginsRetainTaskDescriptionWithoutQueryTimeDiscovery() {
        var classes = mock(IClassScanner.class);
        when(classes.getClassesWithAnnotation(PluginOwned.class)).thenReturn(Map.of(
                Jobs.class, Jobs.class.getAnnotation(PluginOwned.class)));
        var plugins = new PluginRegistry(List.of(
                () -> new PluginDescriptor("test.type", "4.9.0-l", 1, Set.of()),
                () -> new PluginDescriptor("test.method", "4.9.0-l", 1, Set.of())));
        plugins.setEnabled("test.type", false);
        plugins.setEnabled("test.method", false);
        var directory = new PluginContributionDirectory(List.of(new PluginTimerContributionSource(classes)), plugins);
        directory.init();
        assertEquals(List.of("类型后台"), directory.list("test.type").stream().map(PluginContribution::name).toList());
        var method = directory.list("test.method").getFirst();
        assertEquals("方法后台", method.name());
        assertEquals("task", method.kind());
        assertTrue(method.id().contains("#periodic()"));
        directory.list("test.type");
        directory.list("test.method");
        verify(classes, times(1)).getClassesWithAnnotation(PluginOwned.class);
    }

    /**
     * 无实例、无业务执行的声明夹具。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @PluginOwned("test.type")
    @TimerDisplay(category = "test", categoryName = "测试", name = "类型后台")
    private static final class Jobs {
        /** 方法级声明使用独立的插件归属。 */
        @PluginOwned("test.method")
        @TimerDisplay(category = "test", categoryName = "测试", name = "方法后台")
        void periodic() {}

        /** 隐藏声明不进入公开贡献清单。 */
        @TimerDisplay(category = "test", categoryName = "测试", name = "隐藏后台", visible = false)
        void hidden() {}
    }
}
