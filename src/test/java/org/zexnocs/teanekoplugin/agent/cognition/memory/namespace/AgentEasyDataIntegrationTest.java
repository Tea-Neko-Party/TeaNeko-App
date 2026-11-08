package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 复用真实 EasyData 验证跨目标事务、普通缓存及专用配置表路由。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
class AgentEasyDataIntegrationTest {
    /** 验证事务视图的边界、提交失效、回滚保留旧值及与原任务写入混用。 */
    @Test void atomicWritesCoordinateCacheAndRollback() {
        try (var db = new NamespaceDatabaseFixture()) {
            var data = db.data.inNamespace("事务测试"); var dto = data.get("目标");
            dto.getTaskConfig("准备").set("值", "原值").pushAsync().toCompletableFuture().join();
            assertEquals("原值", dto.get("值"));
            assertThrows(IllegalStateException.class, () -> dto.atomic().find("值", EasyDataLock.WRITE));
            data.transaction("提交", () -> {
                var row = dto.atomic().find("值", EasyDataLock.WRITE); assertNotNull(row);
                row.setValue("新值"); dto.atomic().save(row);
                assertEquals("原值", dto.get("值"));
                assertThrows(IllegalArgumentException.class, () -> data.get("其他目标").atomic().save(row));
                return true;
            }).toCompletableFuture().join();
            assertEquals("新值", dto.get("值"));
            assertThrows(CompletionException.class, () -> data.transaction("回滚", () -> {
                var atomic = dto.atomic(); atomic.save(atomic.create("候选", "不能提交"));
                throw new IllegalStateException("模拟业务拒绝。");
            }).toCompletableFuture().join());
            assertNull(dto.get("候选")); assertEquals("新值", dto.get("值"));
            dto.getTaskConfig("原任务写入").set("值", "最终值").pushAsync().toCompletableFuture().join();
            assertEquals("最终值", dto.get("值"));
        }
    }

    /** 验证 /cfg 注册、更新读取和注销均使用 Agent 专用表并复用同一缓存。 */
    @Test void configRegistrationUsesDedicatedAgentStorage() {
        try (var db = new NamespaceDatabaseFixture()) {
            var register = new ConfigDataRegisterService(mock(ILogger.class), db.easyDataService);
            var manager = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
            var dto = db.data.inNamespace("config").get("agent");
            assertSame(dto, register.getDto(manager)); assertFalse(dto.has("scope"));
            register.registerConfigAsync(manager, "scope").toCompletableFuture().join();
            assertTrue(dto.has("scope")); assertNotNull(db.storedValue("config", "agent", "scope"));
            assertTrue(register.unregisterConfigAsync(manager, "scope").toCompletableFuture().join());
            assertFalse(dto.has("scope")); assertNull(db.storedValue("config", "agent", "scope"));
        }
    }
}
