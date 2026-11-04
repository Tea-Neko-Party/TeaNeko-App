package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.adapter.web.AgentChatSettingsSection;
import org.zexnocs.teanekoagent.adapter.web.EasyDataChatTargetStore;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsDirectory;
import org.zexnocs.teanekocore.database.configdata.*;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用事务数据库夹具验证配置并发、审计回滚与聊天映射，不连接真实数据库。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证继承泛型、别名、空值、敏感字段及显式 JSON 类型约束。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
@Timeout(20)
class ConfigEditIntegrationTest {
    /** 验证真实配置事务与通用投影使用相同的字段身份和泛型。 */
    @Test
    void genericJsonFieldsPreserveTypesAliasesAndSecretBoundaries() {
        try (var db = new NamespaceDatabaseFixture()) {
            var registration = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var edits = editor(db, registration);
            var manager = ComplexManager.class.getAnnotation(ConfigManager.class);
            edits.changeJson(manager, KEY, "admin", new ConfigEdits.Change(UUID.randomUUID(), "absent",
                    ConfigEdits.Action.REGISTER, Map.of()), ALLOWED).toCompletableFuture().join();
            var before = edits.read(manager, KEY, ALLOWED).toCompletableFuture().join();
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
            var section = new ChatSettingsDirectory(scanner, db.mapper).entries().get("complex").section();
            var view = section.view(new ChatTargets.Resolved("test", "a", "t", KEY, "测试群", "GROUP", "1", false),
                    new ConfigEdits.Snapshot(true, "test", "{\"oldItems\":[{\"name\":\"茶\",\"weight\":4}],\"secret\":\"不能回显\"}"))
                    .toCompletableFuture().join();
            assertFalse(db.mapper.writeValueAsString(view).contains("不能回显"));
            assertTrue(view.fields().stream().filter(field -> field.name().equals("items")).findFirst().orElseThrow().value().contains("4"));
        }
    }

    /**
     * 泛型父配置，别名与可空字段必须由实际解析器识别。
     * @param <T> 真实条目类型
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
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
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public static class ComplexData extends GenericData<Item> {
        public int count = 1;
        @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        public String secret = "默认秘密";
    }

    /**
     * 具有数值字段的真实泛型条目。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public static class Item {
        public String name = "";
        public int weight;
    }

    /**
     * 测试声明仍使用原专用存储及配置注册合同。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
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
            var first = patch(before.revision(), Map.of("historyReadEnabled", "false"));
            edits.change(MANAGER, KEY, "admin", first, ALLOWED).toCompletableFuture().join();
            assertTrue(edits.change(MANAGER, KEY, "admin", first, ALLOWED).toCompletableFuture().join().replayed());
            assertThrows(CompletionException.class, () -> edits.change(MANAGER, KEY, "admin",
                    patch(before.revision(), Map.of("historyReadEnabled", "true")), ALLOWED).toCompletableFuture().join());
            var current = edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join();
            var rejected = patch(current.revision(), Map.of("historyReadEnabled", "true"));
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
                        pending.set(legacy.setRuleConfigFieldAsync(MANAGER, KEY, "memoryReadEnabled", "false"));
                    } catch (Exception failure) {
                        throw new IllegalStateException("测试指令准备失败。", failure);
                    }
                }
            });
            edits.change(MANAGER, KEY, "admin", patch(before.revision(), Map.of("historyReadEnabled", "false")),
                    ALLOWED).toCompletableFuture().join();
            Objects.requireNonNull(pending.get()).toCompletableFuture().join();
            var body = db.mapper.readTree(edits.read(MANAGER, KEY, ALLOWED).toCompletableFuture().join().json());
            assertFalse(body.get("historyReadEnabled").asBoolean());
            assertFalse(body.get("memoryReadEnabled").asBoolean());
            assertEquals("保留值", body.get("futureExtension").asString());
        }
    }

    /** 不同账号目录不能交叉引用目标，但同一真实配置继续按原规则共享。 */
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
            var bindings = mock(MemoryNamespaceBindings.class);
            when(bindings.readReference(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            var section = new AgentChatSettingsSection(db.mapper, bindings);
            var view = section.view(target, new ConfigEdits.Snapshot(false, "absent",
                    db.mapper.writeValueAsString(new AgentScopeConfigData()))).toCompletableFuture().join();
            assertFalse(view.fields().stream().filter(field -> field.name().equals("memoryNamespace"))
                    .findFirst().orElseThrow().editable());
            assertThrows(IllegalArgumentException.class,
                    () -> section.validate(patch("absent", Map.of("memoryNamespace", "forged"))));
            assertFalse(db.mapper.writeValueAsString(view).contains(KEY));
        }
    }
}
