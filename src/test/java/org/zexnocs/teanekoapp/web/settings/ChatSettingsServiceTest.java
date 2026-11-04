package org.zexnocs.teanekoapp.web.settings;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证自动发现不等于授权，客户端适用范围在读取和提交之前检查。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
class ChatSettingsServiceTest {
    /** 自动目录需要本地授权，平台专用管理器不能借其他客户端执行。 */
    @Test
    void discoversManagersButChecksGroupClientAndPluginBeforeDatabase() {
        var scanner = mock(IBeanScanner.class);
        var general = new GeneralManager();
        var platform = new PlatformManager();
        when(scanner.getBeansWithAnnotation(ConfigManager.class)).thenReturn(Map.of(
                "general", new IndependentPair<>(GeneralManager.class.getAnnotation(ConfigManager.class), general),
                "platform", new IndependentPair<>(PlatformManager.class.getAnnotation(ConfigManager.class), platform)));
        doReturn(GeneralManager.class).when(scanner).getBeanClass(general);
        doReturn(PlatformManager.class).when(scanner).getBeanClass(platform);
        var directory = new ChatSettingsDirectory(scanner, new ObjectMapper());
        var policy = new WebSecurityFileConfig();
        var files = mock(IFileConfigService.class);
        when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(policy);
        var plugins = mock(PluginRegistry.class);
        when(plugins.isEnabled("test.plugin")).thenReturn(true);
        var clients = mock(ClientManagementDirectory.class);
        when(clients.guard(eq("web"), any())).thenAnswer(call -> call.getArgument(1));
        var adapter = mock(IClientManagementAdapter.class);
        var target = new ChatTargets.Resolved("web", "bot", "room", "web-group@room", "测试群", "GROUP", "room", false);
        when(adapter.resolve("bot", "room")).thenReturn(CompletableFuture.completedStage(target));
        when(adapter.configNamespaces(target)).thenReturn(Set.of("teaneko-group"));
        doAnswer(call -> {
            Function<IClientManagementAdapter, ?> action = call.getArgument(1);
            return action.apply(adapter);
        }).when(clients).invoke(eq("web"), any());
        var edits = mock(ConfigEditService.class);
        var service = new ChatSettingsService(directory, clients, edits, plugins, files);
        assertTrue(service.sections(() -> {}).isEmpty());
        // 2026/11/04：已有混合大小写管理器可单独授权，不能因自动发现阻断整个目录。
        policy.setSettingsSections(java.util.List.of("sauceNAO"));
        assertEquals("sauceNAO", policy.snapshot().settingsSections().getFirst());
        assertEquals(1, service.sections(() -> {}).size());
        policy.setSettingsSections(java.util.List.of());
        policy.setSettingsGroups(java.util.List.of("general"));
        assertEquals(2, service.sections(() -> {}).size());
        assertEquals("sauceNAO", service.sections("web", "bot", "room", () -> {}).toCompletableFuture().join().getFirst().id());
        assertThrows(CompletionException.class, () -> service.read("web", "bot", "room", "platform", () -> {}).toCompletableFuture().join());
        verifyNoInteractions(edits);
        when(plugins.isEnabled("test.plugin")).thenReturn(false);
        assertEquals(1, service.sections(() -> {}).size());
        when(scanner.getBeansWithAnnotation(ConfigManager.class)).thenReturn(Map.of(
                "a", new IndependentPair<>(GeneralManager.class.getAnnotation(ConfigManager.class), general),
                "b", new IndependentPair<>(GeneralManager.class.getAnnotation(ConfigManager.class), general)));
        assertThrows(IllegalArgumentException.class, () -> new ChatSettingsDirectory(scanner, new ObjectMapper()).entries());
    }

    /**
     * 通用群配置声明。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    @ConfigManager(value = "sauceNAO", description = "通用配置", namespaces = "teaneko-group")
    public static class GeneralManager {}

    /**
     * 属于可关闭插件的平台专用声明。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    @PluginOwned("test.plugin")
    @ConfigManager(value = "platform", description = "平台配置", namespaces = "onebot-group")
    public static class PlatformManager {}
}
