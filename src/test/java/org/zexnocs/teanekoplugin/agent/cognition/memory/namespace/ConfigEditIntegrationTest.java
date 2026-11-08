package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.integration.web.AgentChatSettingsSection;
import org.zexnocs.teanekoagent.integration.web.EasyDataChatTargetStore;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsDirectory;
import org.zexnocs.teanekocore.database.configdata.ConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.ConfigDataSetService;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.database.configdata.IConfigFieldCheckerScanner;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 复用事务数据库夹具验证配置并发、审计回滚与聊天映射，不连接真实数据库。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证继承泛型、别名、空值、敏感字段及显式 JSON 类型约束。
 * <br>2026/11/04：验证登记状态与真实配置行一致，读取不自动登记配置。
 * <br>2026/11/04：验证网页私聊默认启用、已有关闭保留及原子初始化的授权回滚。
 *
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：配置目录显式初始化，业务查询复用完整发现缓存。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Timeout(20)
class ConfigEditIntegrationTest {
    /** 2026/11/04：装配秘密保留在原行锁内合并，旧版本冲突及提交撤权均不能覆盖凭据。 */
    @Test
    void componentSecretsUseExistingTransactionAndVersionBoundary() {
        try (var db = new NamespaceDatabaseFixture()) {
            var registration = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var llm = new org.zexnocs.teanekoagent.component.llm.LLMComponent(
                    new org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser());
            var scanner = mock(IBeanScanner.class);
            when(scanner.getBeansWithAnnotationAndInterface(org.zexnocs.teanekoagent.component.TeaNekoAgentComponent.class,
                    org.zexnocs.teanekoagent.component.IAgentComponent.class)).thenReturn(Map.of("llm",
                    new IndependentPair<>(llm.getClass().getAnnotation(
                            org.zexnocs.teanekoagent.component.TeaNekoAgentComponent.class), llm)));
            doReturn(llm.getClass()).when(scanner).getBeanClass(llm);
            var plugins = new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(() ->
                    new org.zexnocs.teanekocore.plugin.PluginDescriptor("teaneko.agent", "1", 1, java.util.Set.of())));
            var components = new org.zexnocs.teanekoagent.component.AgentComponentRegistry(scanner,
                    new org.zexnocs.teanekocore.plugin.PluginAccess(plugins));
            var checker = new AgentScopeFieldChecker();
            checker.installComponents(components);
            var checkers = mock(IConfigFieldCheckerScanner.class);
            when(checkers.get(AgentScopeFieldChecker.class)).thenReturn(checker);
            var edits = new ConfigEditService(registration, db.easyDataService, db.mapper, checkers);
            var initial = new AgentScopeConfigData();
            initial.setComponents("""
                    {"version":1,"components":{"llm":{"source":"custom","configuration":{
                    "adapter":"deepseek","model":"test-model","max-tokens":128,
                    "context-window-tokens":8192,"api-key":"private-test-secret"}}}}
                    """);
            edits.initializeIfAbsent(MANAGER, KEY, initial, ALLOWED).toCompletableFuture().join();
            var before = edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join();
            String preview = components.editor(before.json());
            assertFalse(preview.contains("private-test-secret"));
            var patch = patch(before.revision(), Map.of("components", preview.replace("test-model", "next-model")));
            edits.change(MANAGER, KEY, "admin", patch, ALLOWED).toCompletableFuture().join();
            var after = edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join();
            assertTrue(after.json().contains("private-test-secret"));
            assertTrue(after.json().contains("next-model"));
            assertThrows(CompletionException.class, () -> edits.change(MANAGER, KEY, "admin",
                    patch(before.revision(), Map.of("components", preview)), ALLOWED).toCompletableFuture().join());
            var checks = new AtomicInteger();
            assertThrows(CompletionException.class, () -> edits.change(MANAGER, KEY, "admin",
                    patch(after.revision(), Map.of("components", preview)), () -> {
                        if (checks.incrementAndGet() > 1) {
                            throw new SecurityException("提交前撤权。");
                        }
                    }).toCompletableFuture().join());
            assertEquals(after, edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join());
        }
    }

    /** 2026/11/04：默认初始化仅写入缺失私聊，不覆盖显式关闭，撤权时不残留新行。 */
    @Test
    void privateAgentDefaultsPreserveExistingDecisionsAndRollbackOnRevocation() {
        try (var db = new NamespaceDatabaseFixture()) {
            var registration = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var edits = editor(db, registration);
            // 2026/11/07：网页私聊默认业务由所属插件提供，沿用原配置事务入口。
            var preparation = new org.zexnocs.teanekoplugin.web.common.WebPrivateAgentPreparation(edits);
            var room = new org.zexnocs.teanekoclient.web.WebChat.Room("private", "PRIVATE", "测试私聊",
                    "private@test", 1, List.of(), java.time.Instant.now());
            preparation.prepare(room, ALLOWED).toCompletableFuture().join();
            var enabled = edits.read(MANAGER, room.scope(), ALLOWED).toCompletableFuture().join();
            assertTrue(db.mapper.readValue(enabled.json(), AgentScopeConfigData.class).isEnabled());
            edits.change(MANAGER, room.scope(), "admin", patch(enabled.revision(), Map.of("enabled", "false")),
                    ALLOWED).toCompletableFuture().join();
            preparation.prepare(room, ALLOWED).toCompletableFuture().join();
            assertFalse(db.mapper.readValue(edits.read(MANAGER, room.scope(), ALLOWED).toCompletableFuture().join().json(),
                    AgentScopeConfigData.class).isEnabled());
            var group = new org.zexnocs.teanekoclient.web.WebChat.Room("group", "GROUP", "测试群",
                    "web-group@test", 1, List.of(), java.time.Instant.now());
            preparation.prepare(group, ALLOWED).toCompletableFuture().join();
            assertFalse(edits.registered(MANAGER, group.scope(), ALLOWED).toCompletableFuture().join());
            var initial = new AgentScopeConfigData();
            initial.setEnabled(true);
            var checks = new AtomicInteger();
            assertThrows(CompletionException.class, () -> edits.initializeIfAbsent(MANAGER, "private@revoked", initial,
                    () -> { if (checks.incrementAndGet() > 1) { throw new SecurityException("初始化已撤权。"); } })
                    .toCompletableFuture().join());
            assertFalse(edits.registered(MANAGER, "private@revoked", ALLOWED).toCompletableFuture().join());
        }
    }

    /**
     * 2026/11/04：真实配置事务、通用投影及登记状态使用同一持久化身份。
     * 2026/11/05：目录发现由初始化执行，投影查询不扫描 Bean。
     */
    @Test
    void genericJsonFieldsPreserveTypesAliasesAndSecretBoundaries() {
        try (var db = new NamespaceDatabaseFixture()) {
            var registration = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var edits = editor(db, registration);
            var manager = ComplexManager.class.getAnnotation(ConfigManager.class);
            assertFalse(edits.registered(manager, KEY, ALLOWED).toCompletableFuture().join());
            edits.changeJson(manager, KEY, "admin", new ConfigEdits.Change(UUID.randomUUID(), "absent",
                    ConfigEdits.Action.REGISTER, Map.of()), ALLOWED).toCompletableFuture().join();
            var before = edits.read(manager, KEY, ALLOWED).toCompletableFuture().join();
            assertTrue(edits.registered(manager, KEY, ALLOWED).toCompletableFuture().join());
            edits.changeJson(manager, KEY, "admin", patch(before.revision(), Map.of("items", "[{\"name\":\"茶\",\"weight\":3}]",
                    "note", "null", "count", "2")), ALLOWED).toCompletableFuture().join();
            var current = edits.read(manager, KEY, ALLOWED).toCompletableFuture().join();
            var parsed = db.mapper.readValue(current.json(), ComplexData.class);
            assertEquals(3, parsed.items.getFirst().weight);
            assertTrue(db.mapper.readTree(current.json()).has("items-list"));
            assertFalse(db.mapper.readTree(current.json()).has("items"));
            assertThrows(CompletionException.class, () -> edits.changeJson(manager, KEY, "admin",
                    patch(current.revision(), Map.of("count", "2.5")), ALLOWED).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> edits.changeJson(manager, KEY, "admin",
                    patch(current.revision(), Map.of("items", "null")), ALLOWED).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> edits.changeJson(manager, KEY, "admin",
                    patch(current.revision(), Map.of("secret", "\"new-secret\"")), ALLOWED).toCompletableFuture().join());
            assertEquals(current, edits.read(manager, KEY, ALLOWED).toCompletableFuture().join());
            var scanner = mock(IBeanScanner.class);
            var bean = new ComplexManager();
            when(scanner.getBeansWithAnnotation(ConfigManager.class)).thenReturn(Map.of("complex", new IndependentPair<>(manager, bean)));
            doReturn(ComplexManager.class).when(scanner).getBeanClass(bean);
            var directory = new ChatSettingsDirectory(scanner, db.mapper);
            directory.init();
            var section = directory.entries().get("complex").section();
            var view = section.view(new ChatTargets.Resolved("test", "a", "t", KEY, "测试群", "GROUP", "1", false),
                    new ConfigEdits.Snapshot(true, "test", "{\"oldItems\":[{\"name\":\"茶\",\"weight\":4}],\"secret\":\"不能回显\"}"))
                    .toCompletableFuture().join();
            assertFalse(db.mapper.writeValueAsString(view).contains("不能回显"));
            assertTrue(view.fields().stream().filter(field -> field.name().equals("items")).findFirst().orElseThrow().value().contains("4"));
            edits.changeJson(manager, KEY, "admin", new ConfigEdits.Change(UUID.randomUUID(), current.revision(),
                    ConfigEdits.Action.UNREGISTER, Map.of()), ALLOWED).toCompletableFuture().join();
            assertFalse(edits.registered(manager, KEY, ALLOWED).toCompletableFuture().join());
        }
    }

    /**
     * 泛型父配置，别名与可空字段必须由实际解析器识别。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param <T> 真实条目类型
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.9.0-l
     */
    public static class GenericData<T> implements IConfigData {
        @Description("条目列表")
        @com.fasterxml.jackson.annotation.JsonProperty("items-list")
        @com.fasterxml.jackson.annotation.JsonAlias("oldItems")
        public List<T> items = new ArrayList<>();
        @Description("说明")
        public @org.jspecify.annotations.Nullable String note;
    }

    /**
     * 配置实际类型用于测试敏感写入边界。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.9.0-l
     */
    public static class ComplexData extends GenericData<Item> {
        public int count = 1;
        @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        public String secret = "默认秘密";
    }

    /**
     * 具有数值字段的真实泛型条目。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.9.0-l
     */
    public static class Item {
        public String name = "";
        public int weight;
    }

    /**
     * 测试声明仍使用原专用存储及配置注册合同。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.9.0-l
     */
    @ConfigManager(value = "complex", description = "复杂配置", configType = ComplexData.class, namespaces = "test",
            storage = org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyDataObject.class)
    public static class ComplexManager {}

    private static final ConfigManager MANAGER = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
    private static final String KEY = "onebot-group@123";
    private static final Runnable ALLOWED = () -> {};

    /**
     * @param db 本测试事务边界
     * @param registration 原配置入口
     * @return 复用真实字段校验的版本服务
     */
    private ConfigEditService editor(NamespaceDatabaseFixture db, ConfigDataRegisterService registration) {
        var checkers = mock(IConfigFieldCheckerScanner.class);
        when(checkers.get(AgentScopeFieldChecker.class)).thenReturn(new AgentScopeFieldChecker());
        return new ConfigEditService(registration, db.easyDataService, db.mapper, checkers);
    }

    /**
     * @param revision 已读版本
     * @param fields 局部变更
     * @return 独立操作标识的字段请求
     */
    private ConfigEdits.Change patch(String revision, Map<String, String> fields) {
        return new ConfigEdits.Change(UUID.randomUUID(), revision, ConfigEdits.Action.PATCH, fields);
    }

    /** 同一操作只提交一次，旧版本不能覆盖，新值与审计必须一起回滚。 */
    @Test
    void commitsOnceAndRollsBackWhenAuthorizationExpires() {
        try (var db = new NamespaceDatabaseFixture()) {
            var registration = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var edits = editor(db, registration);
            assertFalse(edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join().registered());
            var register = new ConfigEdits.Change(UUID.randomUUID(), "absent", ConfigEdits.Action.REGISTER, Map.of());
            edits.change(MANAGER, KEY, "admin", register, ALLOWED).toCompletableFuture().join();
            var before = edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join();
            var first = patch(before.revision(), Map.of("failureReplyEnabled", "false"));
            edits.change(MANAGER, KEY, "admin", first, ALLOWED).toCompletableFuture().join();
            assertTrue(edits.change(MANAGER, KEY, "admin", first, ALLOWED).toCompletableFuture().join().replayed());
            assertThrows(CompletionException.class, () -> edits.change(MANAGER, KEY, "admin",
                    patch(before.revision(), Map.of("failureReplyEnabled", "true")), ALLOWED).toCompletableFuture().join());
            var current = edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join();
            var rejected = patch(current.revision(), Map.of("failureReplyEnabled", "true"));
            var checks = new AtomicInteger();
            assertThrows(CompletionException.class, () -> edits.change(MANAGER, KEY, "admin", rejected, () -> {
                if (checks.incrementAndGet() == 3) {
                    throw new SecurityException("测试模拟事务提交前退出登录。");
                }
            }).toCompletableFuture().join());
            assertEquals(current, edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> edits.operation(MANAGER, rejected.operationId(),
                    "admin", ALLOWED).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> edits.operation(MANAGER, first.operationId(),
                    "another-admin", ALLOWED).toCompletableFuture().join());
        }
    }

    /**
     * 已缓存旧配置的指令必须在网页提交后合并当前行，且保留未知扩展字段。
     * @throws Exception 当原指令校验失败时
     */
    @Test
    void legacyCommandMergesCurrentValuesInsteadOfOverwritingTheWebChange() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var registration = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var edits = editor(db, registration);
            registration.registerConfigAsync(MANAGER, KEY).toCompletableFuture().join();
            db.data.transaction("测试注入未来字段", () -> {
                var dto = registration.getDto(MANAGER).atomic();
                var row = Objects.requireNonNull(dto.find(KEY, EasyDataLock.WRITE));
                var body = (ObjectNode) db.mapper.readTree(row.getValue());
                body.put("futureExtension", "保留值");
                row.setValue(db.mapper.writeValueAsString(body));
                dto.save(row);
                return true;
            }).toCompletableFuture().join();
            var reader = new ConfigDataGetService(registration);
            var checkers = mock(IConfigFieldCheckerScanner.class);
            when(checkers.get(AgentScopeFieldChecker.class)).thenReturn(new AgentScopeFieldChecker());
            var legacy = new ConfigDataSetService(reader, mock(ILogger.class), db.mapper, checkers, registration);
            reader.getConfigData(MANAGER, KEY).orElseThrow();
            var before = edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join();
            var pending = new AtomicReference<CompletionStage<Void>>();
            var once = new AtomicBoolean();
            db.beforeCommit.set(() -> {
                if (once.compareAndSet(false, true)) {
                    try {
                        pending.set(legacy.setRuleConfigFieldAsync(MANAGER, KEY, "enabled", "false"));
                    } catch (Exception failure) {
                        throw new IllegalStateException("测试指令准备失败。", failure);
                    }
                }
            });
            edits.change(MANAGER, KEY, "admin", patch(before.revision(), Map.of("failureReplyEnabled", "false")),
                    ALLOWED).toCompletableFuture().join();
            Objects.requireNonNull(pending.get()).toCompletableFuture().join();
            var body = db.mapper.readTree(edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join().json());
            assertFalse(body.get("failureReplyEnabled").asBoolean());
            assertFalse(body.get("enabled").asBoolean());
            assertEquals("保留值", body.get("futureExtension").asString());
        }
    }

    /** 2026/11/07：不同账号不能交叉引用目标，业务空间绑定不再进入宿主表单且仍拒绝普通写入。 */
    @Test
    void resolvesOnlyServerKnownAccountTargetsAndKeepsNamespaceBindingReadOnly() {
        try (var db = new NamespaceDatabaseFixture()) {
            var directory = new EasyDataChatTargetStore(db.data, db.mapper);
            directory.remember(List.of(
                    new ChatTargets.Observation("onebot", "111", "GROUP", "123", KEY, "开发群", "MESSAGE"),
                    new ChatTargets.Observation("onebot", "222", "GROUP", "123", KEY, "开发群", "MESSAGE")),
                    ALLOWED).toCompletableFuture().join();
            var accounts = directory.accounts("onebot").toCompletableFuture().join();
            var first = directory.page("onebot", accounts.getFirst().ref(), "", "GROUP")
                    .toCompletableFuture().join().items().getFirst();
            var target = directory.resolve("onebot", accounts.getFirst().ref(), first.ref()).toCompletableFuture().join();
            assertEquals(KEY, target.scope());
            assertThrows(CompletionException.class, () -> directory.resolve("onebot", accounts.getLast().ref(),
                    first.ref()).toCompletableFuture().join());
            var section = new AgentChatSettingsSection(db.mapper);
            // 2026/11/04：本例只验证目标映射，装配预览由独立组件用例验证。
            var components = mock(org.zexnocs.teanekoagent.component.AgentComponentRegistry.class);
            when(components.editor(anyString())).thenReturn(org.zexnocs.teanekoagent.component.AgentAssembly.MINIMAL);
            section.installComponents(components);
            var view = section.view(target, new ConfigEdits.Snapshot(false, "absent",
                    db.mapper.writeValueAsString(new AgentScopeConfigData()))).toCompletableFuture().join();
            assertTrue(view.fields().stream().noneMatch(field -> field.name().equals("memoryNamespace")));
            assertThrows(IllegalArgumentException.class,
                    () -> section.validate(patch("absent", Map.of("memoryNamespace", "forged"))));
            assertFalse(db.mapper.writeValueAsString(view).contains(KEY));
        }
    }
}
