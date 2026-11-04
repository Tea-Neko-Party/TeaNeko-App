package org.zexnocs.teanekoapp.web.settings;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证管理员直接管理已启用配置，客户端与插件边界在读取和提交之前检查。
 * <br>2026/11/04：取消额外本地白名单，覆盖默认目录、身份拒绝和原平台隔离。
 * <br>2026/11/04：验证简短显示名独立于稳定标识及完整说明。
 * <br>2026/11/04：验证实际登记状态、排序及在途目录查询的会话撤权。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.4-l
 */
class ChatSettingsServiceTest {
    /** 2026/11/04：配置按真实登记状态排序，查询全程保持管理员、插件和客户端边界。 */
    @Test
    void discoversManagersWithoutLocalAllowlistButChecksClientAndPlugin() {
        var scanner = mock(IBeanScanner.class);
        var general = new GeneralManager();
        var platform = new PlatformManager();
        when(scanner.getBeansWithAnnotation(ConfigManager.class)).thenReturn(Map.of(
                "general", new IndependentPair<>(GeneralManager.class.getAnnotation(ConfigManager.class), general),
                "platform", new IndependentPair<>(PlatformManager.class.getAnnotation(ConfigManager.class), platform)));
        doReturn(GeneralManager.class).when(scanner).getBeanClass(general);
        doReturn(PlatformManager.class).when(scanner).getBeanClass(platform);
        var directory = new ChatSettingsDirectory(scanner, new ObjectMapper());
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
        var generalDeclaration = GeneralManager.class.getAnnotation(ConfigManager.class);
        var platformDeclaration = PlatformManager.class.getAnnotation(ConfigManager.class);
        when(edits.registered(eq(generalDeclaration), eq(target.scope()), any()))
                .thenReturn(CompletableFuture.completedStage(true));
        when(edits.registered(eq(platformDeclaration), eq(target.scope()), any()))
                .thenReturn(CompletableFuture.completedStage(false));
        var service = new ChatSettingsService(directory, clients, edits, plugins);
        // 2026/11/04：默认即提供两项已启用配置，不依赖本地白名单。
        assertEquals(2, service.sections(() -> {}).size());
        assertNull(service.sections(() -> {}).getFirst().registered());
        verifyNoInteractions(edits);
        assertThrows(SecurityException.class, () -> service.sections(() -> {
            throw new SecurityException("当前不是管理员。");
        }));
        // 2026/11/04：短名称改变不影响原配置键或完整说明。
        var visible = service.sections("web", "bot", "room", () -> {}).toCompletableFuture().join().getFirst();
        assertEquals("sauceNAO", visible.id());
        assertEquals("图片来源", visible.name());
        assertEquals("通用配置", visible.description());
        assertEquals(Boolean.TRUE, visible.registered());
        assertThrows(CompletionException.class, () -> service.read("web", "bot", "room", "platform", () -> {}).toCompletableFuture().join());
        verify(edits, never()).registered(eq(platformDeclaration), anyString(), any());
        verify(edits, never()).read(any(), anyString(), any());
        // 2026/11/04：已登记的 sauceNAO 排在原本字母序更靠前的 platform 之前。
        when(adapter.configNamespaces(target)).thenReturn(Set.of("teaneko-group", "onebot-group"));
        var ordered = service.sections("web", "bot", "room", () -> {}).toCompletableFuture().join();
        assertEquals(java.util.List.of("sauceNAO", "platform"), ordered.stream().map(ChatSettings.Section::id).toList());
        assertEquals(Boolean.FALSE, ordered.getLast().registered());
        // 2026/11/04：数据库读取完成前会话已撤销时，不返回登记状态。
        var delayed = new CompletableFuture<Boolean>();
        when(edits.registered(eq(generalDeclaration), eq(target.scope()), any())).thenReturn(delayed);
        var authorized = new AtomicBoolean(true);
        var waiting = service.sections("web", "bot", "room", () -> {
            if (!authorized.get()) {
                throw new SecurityException("会话已撤销。");
            }
        });
        authorized.set(false);
        delayed.complete(true);
        assertThrows(CompletionException.class, () -> waiting.toCompletableFuture().join());
        when(plugins.isEnabled("test.plugin")).thenReturn(false);
        assertEquals(1, service.sections(() -> {}).size());
        when(scanner.getBeansWithAnnotation(ConfigManager.class)).thenReturn(Map.of(
                "a", new IndependentPair<>(GeneralManager.class.getAnnotation(ConfigManager.class), general),
                "b", new IndependentPair<>(GeneralManager.class.getAnnotation(ConfigManager.class), general)));
        assertThrows(IllegalArgumentException.class, () -> new ChatSettingsDirectory(scanner, new ObjectMapper()).entries());
    }

    /**
     * 通用群配置声明。
     * <br>2026/11/04：显示名称不参与配置标识解析。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.4-l
     */
    // 2026/11/04：用短名称验证实际配置发现投影。
    @ConfigManager(value = "sauceNAO", displayName = "图片来源", description = "通用配置", namespaces = "teaneko-group")
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
