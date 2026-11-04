package org.zexnocs.teanekoapp.web.client;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsDirectory;
import org.zexnocs.teanekoapp.web.settings.IChatSettingsSection;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 验证旧 Agent 空目录游标不会掩盖迁移后的通用群聊和私聊配置。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.6-l
 * @version 4.8.0-l
 */
class ConfigTargetImporterTest {
    /** 2026/11/04：原管理器各自读其存储，目录不猜测机器人账号，不重复导入已完成分页。 */
    @Test
    void recoversGeneralConfigurationsEvenWhenOldAgentImportWasAlreadyComplete() {
        var store = mock(IChatTargetStore.class);
        var configs = mock(ConfigEditService.class);
        var users = mock(ITeaUserService.class);
        var directory = mock(ChatSettingsDirectory.class);
        var client = mock(ITeaNekoClient.class);
        when(client.getClientId()).thenReturn("onebot");
        var cursor = new AtomicReference<>("!done");
        when(store.importCursor("onebot")).thenAnswer(ignored -> CompletableFuture.completedStage(cursor.get()));
        when(store.imported(eq("onebot"), anyString())).thenAnswer(call -> {
            cursor.set(call.getArgument(1));
            return CompletableFuture.completedStage(null);
        });
        var observed = new ArrayList<ChatTargets.Observation>();
        when(store.remember(anyList(), any())).thenAnswer(call -> {
            List<ChatTargets.Observation> batch = call.getArgument(0);
            observed.addAll(batch);
            return CompletableFuture.completedStage(null);
        });
        var uuid = UUID.randomUUID();
        when(users.getPlatformId(client, uuid)).thenReturn("12345");
        var general = Legacy.class.getAnnotation(ConfigManager.class);
        var agent = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
        var generalSection = mock(IChatSettingsSection.class);
        var agentSection = mock(IChatSettingsSection.class);
        when(generalSection.manager()).thenReturn(general);
        when(agentSection.manager()).thenReturn(agent);
        when(directory.entries()).thenReturn(Map.of("legacy", new ChatSettingsDirectory.Entry("legacy", "general", Legacy.class, generalSection),
                "agent", new ChatSettingsDirectory.Entry("agent", "agent", AgentScopeConfigManager.class, agentSection)));
        when(configs.keys(eq(general), eq(""), eq(100))).thenReturn(CompletableFuture.completedStage(
                new ConfigEdits.Keys(List.of("onebot-group@100", "private@" + uuid), "onebot-group@100")));
        when(configs.keys(eq(general), eq("onebot-group@100"), eq(100)))
                .thenReturn(CompletableFuture.completedStage(new ConfigEdits.Keys(List.of("onebot-group@200"), "")));
        when(configs.keys(eq(agent), eq(""), eq(100))).thenReturn(CompletableFuture.completedStage(new ConfigEdits.Keys(List.of(), "")));
        var importer = new ConfigTargetImporter(store, configs, users, directory, mock(PluginRegistry.class),
                new ObjectMapper(), Runnable::run);
        importer.discover(client, () -> {}).toCompletableFuture().join();
        assertEquals(Set.of("GROUP", "PRIVATE"), observed.stream().map(ChatTargets.Observation::kind).collect(java.util.stream.Collectors.toSet()));
        assertTrue(observed.stream().allMatch(value -> value.self().isEmpty()));
        assertTrue(cursor.get().startsWith("v2:{"));
        importer.discover(client, () -> {}).toCompletableFuture().join();
        assertEquals(3, observed.size());
        assertEquals("v2:!done", cursor.get());
        importer.discover(client, () -> {}).toCompletableFuture().join();
        verify(configs, times(1)).keys(general, "", 100);
        verify(configs, times(1)).keys(general, "onebot-group@100", 100);
        verify(configs, times(1)).keys(agent, "", 100);
    }

    /**
     * 原通用配置管理器声明，与 Agent 使用不同的存储入口。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.6-l
     * @version 4.7.6-l
     */
    @ConfigManager(value = "legacy", description = "迁移配置", namespaces = {"teaneko-group", "teaneko-private"})
    static class Legacy {}
}
