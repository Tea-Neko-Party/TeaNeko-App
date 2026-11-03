package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.adapter.web.AgentChatSettingsSection;
import org.zexnocs.teanekoagent.adapter.web.EasyDataChatTargetStore;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekocore.database.configdata.*;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.logger.ILogger;
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
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Timeout(20)
class ConfigEditIntegrationTest {
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
