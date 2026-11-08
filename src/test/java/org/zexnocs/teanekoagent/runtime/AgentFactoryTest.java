package org.zexnocs.teanekoagent.runtime;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 验证实例实体、参数快照、拥有关系与关闭资源相互独立，关闭回调不占注册表锁。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：拒绝路径若错误地交付句柄，测试也负责关闭，避免失败时泄漏资源。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class AgentFactoryTest {
    /**
     * 提供不触发模型默认值读取的只读定义。
     * @return 测试定义
     */
    private AgentDefinition definition() {
        return new AgentDefinition("test", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    }

    /** 验证共享定义的两个实例具有独立 ID、revision、关闭状态和监听。 */
    @Test void sameDefinitionCreatesIndependentEntities() {
        try (var registry = new AgentRegistry()) {
            var factory = new AgentFactory(registry);
            var definition = definition();
            var first = factory.create(definition); var second = factory.create(definition);
            var original = first.snapshot();
            var firstClosed = new AtomicInteger(); var secondClosed = new AtomicInteger();
            first.onClose(firstClosed::incrementAndGet); second.onClose(secondClosed::incrementAndGet);
            assertNotEquals(first.id(), second.id());
            assertSame(definition, first.definition());
            first.close(); first.close();
            assertFalse(original.require(AgentComponents.CLOSED));
            assertTrue(first.snapshot().require(AgentComponents.CLOSED));
            assertEquals(1, first.snapshot().revision());
            assertFalse(second.isClosed());
            assertEquals(0, second.snapshot().revision());
            assertEquals(1, firstClosed.get()); assertEquals(0, secondClosed.get());
            assertReadOnly(registry.snapshots());
        }
    }

    /**
     * 以通用集合引用验证公开快照拒绝修改，保留有意的异常契约测试。
     * @param values 待验证的快照列表
     */
    private static void assertReadOnly(List<?> values) {
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    /** 验证原始参数、嵌套集合及后续参数副本的修改不会改变定义组件。 */
    @Test @SuppressWarnings("unchecked") void modelAndActivitySnapshotsCannotBeMutatedThroughAliases() {
        var nested = new ArrayList<Object>(List.of("原值"));
        var source = LLMModelOptions.builder().model("原模型").metadata(Map.of("state", nested)).build();
        var activities = EnumSet.allOf(AgentActivity.class);
        var definition = new AgentDefinition("test", new LLMModelOptionsSnapshot(source), activities);
        source.setModel("已修改"); nested.set(0, "已修改"); activities.clear();
        var first = definition.modelOptions().toOptions(); first.setModel("修改副本");
        assertEquals("原模型", definition.modelOptions().toOptions().getModel());
        assertEquals(List.of("原值"), first.getMetadata().get("state"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) first.getMetadata().get("state")).clear());
        assertEquals(AgentActivity.values().length, definition.activities().size());
        assertThrows(LLMConfigurationException.class, () -> new LLMModelOptionsSnapshot(
                LLMModelOptions.builder().metadata(Map.of("credentials", "测试敏感值")).build()));
    }

    /** 验证移除在回调前取消拥有关系，重复登记不会替换旧实例。 */
    @Test void duplicateRegistrationAndRemovalKeepOwnershipExplicit() {
        try (var registry = new AgentRegistry()) {
            var handle = new AgentFactory(registry).create(definition());
            assertThrows(IllegalStateException.class, () -> registry.register(handle));
            handle.onClose(() -> assertTrue(registry.find(handle.id()).isEmpty()));
            assertTrue(registry.remove(handle.id()));
            assertFalse(registry.remove(handle.id()));
            assertTrue(handle.isClosed());
        }
    }

    /**
     * 验证关闭回调可等待其他线程重入注册表，不在回调期间持有注册表锁。
     */
    @Test void closeCallbacksRunOutsideRegistryLock() {
        try (var registry = new AgentRegistry(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var factory = new AgentFactory(registry);
            var first = factory.create(definition()); var second = factory.create(definition());
            first.onClose(() -> {
                try { assertTrue(executor.submit(() -> registry.remove(second.id())).get(3, TimeUnit.SECONDS)); }
                catch (Exception failure) { throw new AssertionError("关闭回调不能被注册表锁阻塞。", failure); }
            });
            assertTrue(registry.remove(first.id()));
            assertTrue(second.isClosed());
        }
    }

    /** 验证已完成活动可释放关闭监听，关闭后的新监听仍立即得到通知。 */
    @Test void closeRegistrationsAreRemovableAndLateListenersAreNotLost() {
        try (var registry = new AgentRegistry()) {
            var handle = new AgentFactory(registry).create(definition());
            var called = new AtomicInteger();
            var registration = handle.onClose(called::incrementAndGet); registration.close();
            handle.close();
            assertEquals(0, called.get());
            handle.onClose(called::incrementAndGet).close();
            assertEquals(1, called.get());
        }
    }

    /**
     * 验证登记失败后工厂关闭未移交句柄，避免残留可运行实例。
     * 2026/11/05：拒绝路径若错误地交付句柄，测试也负责关闭，避免失败时泄漏资源。
     */
    @Test void rejectedFactoryRegistrationClosesUnownedHandle() {
        var registry = mock(IAgentRegistry.class);
        var created = new AtomicReference<AgentHandle>();
        doAnswer(invocation -> {
            created.set(invocation.getArgument(0));
            throw new IllegalStateException("测试注册表已关闭。");
        }).when(registry).register(any());
        // 2026/11/05：若工厂错误地创建成功，失败断言前也关闭意外交付的资源。
        assertThrows(IllegalStateException.class, () -> {
            try (var unexpected = new AgentFactory(registry).create(definition())) {
                fail("登记被拒绝后仍创建了实例：" + unexpected.id());
            }
        });
        assertTrue(created.get().isClosed());
    }

    /**
     * 验证并发创建与关闭不会遗留已经交付但未关闭的实例。
     * 2026/11/05：拒绝路径若错误地交付句柄，测试也负责关闭，避免失败时泄漏资源。
     * @throws Exception 当受控并发任务失败时
     */
    @Test void concurrentCreateAndShutdownDoNotLeakRegisteredInstances() throws Exception {
        try (var registry = new AgentRegistry(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var factory = new AgentFactory(registry);
            var tasks = new ArrayList<Future<AgentHandle>>();
            for (int i = 0; i < 50; i++) {
                tasks.add(executor.submit(() -> {
                    try {
                        return factory.create(definition());
                    } catch (IllegalStateException closed) {
                        return null;
                    }
                }));
            }
            registry.close();
            for (var task : tasks) {
                var handle = task.get(3, TimeUnit.SECONDS);
                if (handle != null) {
                    assertTrue(handle.isClosed());
                }
            }
            assertTrue(registry.snapshots().isEmpty());
            // 2026/11/05：拒绝路径出现回归时仍释放意外资源，不影响正常的注册表生命周期。
            assertThrows(IllegalStateException.class, () -> {
                try (var unexpected = factory.create(definition())) {
                    fail("已关闭注册表仍创建了实例：" + unexpected.id());
                }
            });
        }
    }
}
