package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.integration.config.AgentAssemblyUpgrade;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.IConfigFieldCheckerScanner;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 复用原事务夹具验证持久装配升级的行版本、幂等、失败保留及提交回滚。
 * <br>2026/11/07：不连接真实数据库，组件转换由其专属合同测试覆盖。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Timeout(20)
class AgentAssemblyUpgradeTest {
    /**
     * 迁移保留未知字段与秘密，冲突行原文不变，重复升级不改变行版本。
     * @throws Exception 当事务有界等待失败时
     */
    @Test
    void migrationPreservesHiddenDataAndRejectsConflictWithoutRewriting() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var logger = mock(ILogger.class);
            var registration = new ConfigDataRegisterService(logger, db.easyDataService);
            var edits = new ConfigEditService(registration, db.easyDataService, db.mapper,
                    mock(IConfigFieldCheckerScanner.class));
            var registry = mock(AgentComponentRegistry.class);
            String before = "{\"version\":1,\"components\":{\"shared-memory\":{}}}";
            String after = "{\"version\":1,\"components\":{\"memory\":{\"crossChatRecallEnabled\":true}}}";
            String conflicting = "{\"version\":1,\"components\":{\"memory\":{},\"shared-memory\":{}}}";
            when(registry.normalize(before)).thenReturn(after);
            when(registry.normalize(after)).thenReturn(after);
            when(registry.normalize(conflicting)).thenThrow(new IllegalArgumentException("测试冲突。"));
            var manager = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
            var source = registration.getDto(manager);
            var original = db.mapper.createObjectNode().put("components", before).put("enabled", false);
            original.putObject("future-extension").put("secret", "保留原秘密");
            var conflict = db.mapper.createObjectNode().put("components", conflicting).toString();
            var flat = db.mapper.createObjectNode().put("enabled", true).put("historyMaxMessages", 23)
                    .put("future-extension", "保留旧字段");
            when(registry.normalizeConfiguration(flat.toString())).thenReturn(after);
            var create = source.getTaskConfig("建立旧装配");
            create.addTransactionTask(() -> {
                var transaction = source.atomic();
                transaction.save(transaction.create("legacy", original.toString()));
                transaction.save(transaction.create("conflict", conflict));
                transaction.save(transaction.create("flat", flat.toString()));
            });
            create.pushAsync().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var upgrade = new AgentAssemblyUpgrade(registration, edits, registry, db.mapper, logger);
            upgrade.migrate(List.of("legacy", "conflict", "flat")).toCompletableFuture().get(2, TimeUnit.SECONDS);
            var migrated = edits.read(manager, "legacy", () -> {}).toCompletableFuture().get(2, TimeUnit.SECONDS);
            var content = db.mapper.readTree(migrated.json());
            assertEquals(after, content.path("components").asString());
            assertEquals(original.path("future-extension"), content.path("future-extension"));
            assertFalse(content.path("enabled").asBoolean());
            var flatMigrated = db.mapper.readTree(edits.read(manager, "flat", () -> {}).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).json());
            assertEquals(after, flatMigrated.path("components").asString());
            assertEquals(23, flatMigrated.path("historyMaxMessages").asInt());
            assertEquals("保留旧字段", flatMigrated.path("future-extension").asString());
            assertEquals(conflict, edits.read(manager, "conflict", () -> {}).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).json());
            upgrade.migrate(List.of("legacy")).toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(migrated, edits.read(manager, "legacy", () -> {}).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS));
            verify(logger).warn(eq("Agent 配置升级"), contains("已保留原配置"));

            // 2026/11/07：真实写失败必须回滚并传播，不能被当作配置冲突吞掉。
            var restore = source.getTaskConfig("恢复回滚测试初值");
            restore.addTransactionTask(() -> {
                var transaction = source.atomic();
                var row = transaction.find("legacy", EasyDataLock.WRITE);
                assertNotNull(row);
                row.setValue(original.toString());
                transaction.save(row);
            });
            restore.pushAsync().toCompletableFuture().get(2, TimeUnit.SECONDS);
            db.beforeCommit.set(() -> {
                throw new IllegalStateException("测试提交失败。");
            });
            assertThrows(CompletionException.class,
                    () -> upgrade.migrate(List.of("legacy")).toCompletableFuture().join());
            db.beforeCommit.set(() -> {});
            assertEquals(original.toString(), edits.read(manager, "legacy", () -> {}).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).json());
        }
    }
}
