package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.web.IAgentLibrarySource;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationControl;
import org.zexnocs.teanekoclient.web.WebChat;
import org.zexnocs.teanekoclient.web.WebChatSettings;
import org.zexnocs.teanekoclient.web.WebChatStore;
import org.zexnocs.teanekoclient.web.WebManagementAdapter;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.AgentConsole;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.AgentLayersConsole;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.AgentLayersConsoleService;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.AgentPersonalityLibraryService;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.ProfileLibrarySource;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityManagementCommand;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.ConfiguredPersonalities;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.EasyDataPersonalityStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.file_config.AgentPersonalityDefinition;
import org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.file_config.AgentPersonalityFileConfig;
import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

/**
 * 独立空间使用真实配置投影和原事务，验证无聊天绑定、重放、版本及撤权。
 * <br>2026/11/06：只使用原离线数据库夹具，不连接实际服务。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
@Timeout(20)
class AgentLibraryIntegrationTest {
    /** 独立空间不查询 instance 聊天；混合基础补正按所选身份提交且回执不重复写入。 */
    @Test
    void unboundPersonalityUsesRealCatalogAndOriginalTransactions() {
        try (var db = new NamespaceDatabaseFixture(); var control = ExecutionControl.create()) {
            var file = new AgentPersonalityFileConfig();
            var definition = new AgentPersonalityDefinition();
            definition.setId("helper");
            definition.setDisplayName("测试助手");
            definition.getLearningPolicy().setMutableFields(List.of("speakingStyle"));
            file.setCharacters(List.of(definition));
            file.setDefaultAgentId("helper");
            var files = mock(IFileConfigService.class);
            when(files.requireCurrent(AgentPersonalityFileConfig.class)).thenReturn(file);
            var configs = mock(IConfigDataService.class);
            var configuration = new ConfiguredPersonalities(AgentComponentFixtures.access(configs, mock(AgentScopeConfigManager.class)), files);
            var base = configuration.catalog().getFirst();
            var store = new EasyDataPersonalityStore(db.data, db.mapper);
            var selection = store.resolve(new AgentScope("web", "原聊天"), base, Instant.now()).toCompletableFuture().join();
            var key = selection.state().key();
            var sync = mock(IAgentConversationControl.class);
            when(sync.synchronizePromptContext(any(), anyString(), eq(true), any())).thenReturn(CompletableFuture.completedStage(1));
            var service = new AgentPersonalityLibraryService(store, store, configuration, sync);
            var context = new ManagementContext("web.local-admin", "global", UUID.randomUUID(), control, () -> {});
            var query = new AgentPersonalityLibraryService.Query("READ", null, key, -1, -1, null, null, null, null, null, null);
            var view = assertInstanceOf(AgentPersonalityLibraryService.View.class, service.execute(context, query).toCompletableFuture().join());
            assertEquals(0, view.personality().bindingRevision());
            assertEquals("helper", view.bases().getFirst().id());
            verifyNoInteractions(configs);
            var id = UUID.randomUUID();
            var add = new AgentPersonalityLibraryService.Query("ADD", id, key, 0, 0, null,
                    "speakingStyle", "更简洁", null, base.id(), base.fingerprint());
            var receipt = assertInstanceOf(AgentConsole.ChangeView.class, service.execute(context, add).toCompletableFuture().join());
            assertEquals("APPLIED", receipt.status());
            assertEquals(receipt, service.execute(context, add).toCompletableFuture().join());
            var current = store.state(key).toCompletableFuture().join().orElseThrow();
            assertEquals(1, current.corrections().size());
            assertEquals("helper", current.corrections().getFirst().baseId());
            assertTrue(store.binding(new AgentScope("management", "instance")).toCompletableFuture().join().isEmpty());

            definition.setSpeakingStyle(List.of("修改正文但不修改版本"));
            assertEquals(receipt, service.execute(context, add).toCompletableFuture().join());
            var altered = new AgentPersonalityLibraryService.Query("ADD", id, key, 0, 0, null,
                    "speakingStyle", "同编号不能改变正文", null, base.id(), base.fingerprint());
            assertEquals("CONFLICT", assertInstanceOf(AgentConsole.ChangeView.class,
                    service.execute(context, altered).toCompletableFuture().join()).status());
            var stale = new AgentPersonalityLibraryService.Query("ADD", UUID.randomUUID(), key, 0, 1, null,
                    "speakingStyle", "不能写入", null, base.id(), base.fingerprint());
            assertThrows(CompletionException.class, () -> service.execute(context, stale).toCompletableFuture().join());
            var clone = new AgentPersonalityLibraryService.Query("CLONE", UUID.randomUUID(), key, 0, 1,
                    null, null, null, null, null, null);
            var copied = assertInstanceOf(AgentConsole.ChangeView.class, service.execute(context, clone).toCompletableFuture().join());
            assertNotEquals(key, copied.target());
            assertEquals(1, store.state(copied.target()).toCompletableFuture().join().orElseThrow().corrections().size());
            assertEquals(selection.binding(), store.binding(selection.binding().scope()).toCompletableFuture().join().orElseThrow());
            assertFalse(store.backups(key).toCompletableFuture().join().isEmpty());
            var first = store.page("", 1).toCompletableFuture().join();
            assertEquals(1, first.items().size());
            assertFalse(first.next().isEmpty());
            assertNotEquals(first.items().getFirst().key(), store.page(first.next(), 1).toCompletableFuture().join().items().getFirst().key());
            var unauthorized = new ManagementContext("web.local-admin", "global", UUID.randomUUID(), control,
                    () -> { throw new SecurityException("测试撤权"); });
            assertThrows(SecurityException.class, () -> service.execute(unauthorized, query));
            var beforeRollback = store.state(key).toCompletableFuture().join();
            var beforeBackups = store.backups(key).toCompletableFuture().join();
            var rejectedId = UUID.randomUUID();
            var remove = new PersonalityManagementCommand(rejectedId, current.corrections().getFirst().source(),
                    PersonalityManagementCommand.Action.REMOVE, key, 0, 1, id, null, null, null);
            var checks = new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(CompletionException.class, () -> store.applyUnbound(remove, () -> null, Instant.now(), () -> {
                if (checks.incrementAndGet() == 2) throw new SecurityException("事务末尾撤权");
            }).toCompletableFuture().join());
            assertEquals(beforeRollback, store.state(key).toCompletableFuture().join());
            assertEquals(beforeBackups, store.backups(key).toCompletableFuture().join());
            assertInstanceOf(NoSuchElementException.class, assertThrows(CompletionException.class,
                    () -> store.operation(rejectedId, remove.actor(), () -> {}).toCompletableFuture().join()).getCause());
            var nativeActor = new AgentActorContext(selection.binding().scope(), AgentActorContext.Origin.USER,
                    Optional.of(UUID.randomUUID()), "123", "来源", "目标");
            assertThrows(IllegalArgumentException.class, () -> new PersonalityManagementCommand(UUID.randomUUID(), nativeActor,
                    PersonalityManagementCommand.Action.CLONE, key, 0, 1, null, null, null, null));
        }
    }

    /** 全局层只读真实共享长期目录；画像从事实末页继续读取长期主体及已知映射。 */
    @Test
    void globalLibrariesDoNotInventChatBindingsAndContinueProfiles() {
        try (var control = ExecutionControl.create()) {
            var namespaces = mock(IMemoryNamespaceStore.class);
            var id = MemoryNamespaceId.SHARED;
            var namespace = new MemoryNamespace(id, 0, 0, Instant.now(), null, null);
            when(namespaces.find(id)).thenReturn(CompletableFuture.completedStage(Optional.of(namespace)));
            var longTerm = mock(org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILongTermMemoryStore.class);
            var layers = mock(org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILayeredMemoryStore.class);
            var bindings = mock(MemoryNamespaceBindings.class);
            var memories = new org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories.Result(List.of(), 0, false);
            when(longTerm.query(any(), isNull(), eq(""), eq(20))).thenReturn(CompletableFuture.completedStage(memories));
            var context = new ManagementContext("web.local-admin", "global", UUID.randomUUID(), control, () -> {});
            var service = new AgentLayersConsoleService(layers, longTerm, bindings, namespaces,
                    new org.springframework.mock.env.MockEnvironment());
            var view = assertInstanceOf(AgentLayersConsole.View.class, service.execute(context,
                    new AgentLayersConsole.Query("READ", id.value(), 0, null, "", null, -1, null, null))
                    .toCompletableFuture().join());
            assertSame(memories, view.longTerm());
            assertNull(view.consolidation());
            verifyNoInteractions(layers, bindings);
            verify(longTerm, never()).consolidationState(any(), any(), any());
            var facts = mock(org.zexnocs.teanekoplugin.agent.cognition.memory.admin.IMemoryManagementStore.class);
            when(facts.page(id, 0, null, 40)).thenReturn(CompletableFuture.completedStage(
                    new org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactPage(List.of(), null)));
            var users = mock(org.zexnocs.teanekoapp.teauser.TeaUserDirectory.class);
            var subject = UUID.randomUUID();
            when(users.mappings(subject)).thenReturn(List.of(
                    new org.zexnocs.teanekoapp.teauser.TeaUserDirectory.Mapping("web", "已有映射", "已知昵称")));
            when(longTerm.subjects(any(), eq(""), eq(40))).thenReturn(CompletableFuture.completedStage(
                    new org.zexnocs.teanekoagent.database.easydata.StoredPage<>(List.of(subject), "")));
            var profiles = new ProfileLibrarySource(namespaces, facts, longTerm, users);
            var emptyFacts = profiles.page(context, new IAgentLibrarySource.Query("profiles", "", id.value(), 0))
                    .toCompletableFuture().join();
            assertTrue(emptyFacts.items().isEmpty());
            assertEquals("learned:", emptyFacts.next());
            var learned = profiles.page(context, new IAgentLibrarySource.Query("profiles", emptyFacts.next(), id.value(), 0))
                    .toCompletableFuture().join();
            assertEquals(subject.toString(), learned.items().getFirst().id());
            assertEquals("已知昵称", learned.items().getFirst().name());
            assertEquals("已有映射", learned.items().getFirst().mappings().getFirst().platformId());
            assertEquals("", learned.next());
            assertThrows(CompletionException.class, () -> profiles.page(context,
                    new IAgentLibrarySource.Query("profiles", "", id.value(), 1)).toCompletableFuture().join());
        }
    }

    /** 网页管理发送只使用原房间事务；连接代次和授权变化阻止迟到发送。 */
    @Test
    void webConsoleLeaseSendsWithoutInventingNativeMessages() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new WebChatStore(db.data.inNamespace("library-web-test")::get, db.mapper, () -> new WebChatSettings().limits());
            store.home(UUID.randomUUID(), () -> {}).toCompletableFuture().join();
            var room = store.create(new WebChat.CreateRoom(UUID.randomUUID(), "PRIVATE", "指令台测试", List.of(WebChat.DEFAULT_PROFILE)),
                    "web-test", () -> {}).toCompletableFuture().join();
            var adapter = new WebManagementAdapter(store);
            var target = adapter.resolve(WebChat.SELF, room.id()).toCompletableFuture().join();
            assertEquals("READY", adapter.consoleState(target).toCompletableFuture().join().state());
            var allowed = new AtomicBoolean(true);
            var lease = adapter.consoleLease(target, () -> {
                if (!allowed.get()) throw new SecurityException("测试撤权");
            }).toCompletableFuture().join();
            var messageId = lease.sender().apply("管理消息").toCompletableFuture().join();
            var page = store.page(room.id(), 0, () -> {}).toCompletableFuture().join();
            assertEquals(messageId, page.items().getFirst().id());
            assertTrue(page.items().getFirst().bot());
            allowed.set(false);
            assertThrows(CompletionException.class, () -> lease.sender().apply("不能发送").toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> store.sendManaged(room.id(), room.revision() + 1, "旧代次", () -> {})
                    .toCompletableFuture().join());
            assertEquals(1, store.page(room.id(), 0, () -> {}).toCompletableFuture().join().items().size());
        }
    }
}
