package org.zexnocs.teanekoagent.memory.recall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent._app_config.MemoryFactConfig;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactRecord;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactSource;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证懒读本地配置、失效后恢复及真实装配的 JSON 度量，不启动完整应用。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class MemoryRecallConfigTest {
    /** 验证构造不读取 Environment，默认策略与配置约定一致。 */
    @Test void defaultsAreLazyAndExplicit() {
        var environment = mock(Environment.class); new SpringMemoryRecallDefaults(environment); verifyNoInteractions(environment);
        assertEquals(MemoryRecallSettings.defaults(), new SpringMemoryRecallDefaults(new MockEnvironment()).settings());
    }
    /** 验证每类配置的非法值均固定分类且不泄露原值，修复后下一次读取恢复。 */
    @Test void invalidPropertiesDoNotSilentlyFallBackOrLeak() {
        for (var entry : Map.of("injection-limit", "0", "tool-limit", "21", "hard-limit", "101", "max-characters", "秘密值", "max-scanned", "10001").entrySet()) {
            var environment = new MockEnvironment().withProperty("tea-neko.agent.memory.recall." + entry.getKey(), entry.getValue());
            var defaults = new SpringMemoryRecallDefaults(environment); var error = assertThrows(MemoryRecallException.class, defaults::settings);
            assertEquals(MemoryRecallException.Reason.CONFIGURATION_ERROR, error.reason()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("秘密"));
        }
        var environment = new MockEnvironment().withProperty("tea-neko.agent.memory.recall.hard-limit", "5"); var defaults = new SpringMemoryRecallDefaults(environment);
        assertThrows(MemoryRecallException.class, defaults::settings);
        environment.withProperty("tea-neko.agent.memory.recall.injection-limit", "4").withProperty("tea-neko.agent.memory.recall.tool-limit", "3");
        assertEquals(new MemoryRecallSettings(4, 3, 5, 16000, 1000), defaults.settings());
    }
    /** 验证真实 Bean 构造无 I/O，装配返回具体服务并借用 Core 执行器。 */
    @Test void compositionHasNoStartupIoAndDoesNotOwnExecutor() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var store = mock(IMemoryRecallStore.class); var defaults = mock(IMemoryRecallDefaults.class);
            var service = new MemoryFactConfig().memoryRecallService(store, defaults, new ObjectMapper(), executor); verifyNoInteractions(store, defaults);
            assertInstanceOf(IMemoryRecallService.class, service); assertFalse(executor.isShutdown());
        }
    }
    /** 验证输入 Mapper 开启美化时，装配仍按实际紧凑证据数组计量完整元数据。 */
    @Test void productionMeterUsesCompactEvidenceArray() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var scope = new AgentScope("客户端", "私聊"); var subject = UUID.randomUUID(); var now = Instant.parse("2026-10-29T01:00:00Z");
            var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(subject), "平台用户", "原消息", "目标");
            var value = new MemoryFactValue(MemoryFactValue.Kind.FACT, "含\"引号\"与换行\n", List.of("标签"), 1,
                    MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
            var evidence = MemoryRecallEvidence.from(new MemoryFactRecord(UUID.randomUUID(), MemoryNamespaceId.SHARED, 0, 0, subject,
                    value, new MemoryFactSource(UUID.randomUUID(), "动作", actor), now, now, false));
            var mapper = new ObjectMapper().rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
            var service = new MemoryFactConfig().memoryRecallService((id, gen, max) -> CompletableFuture.completedFuture(new MemoryRecallScan(id, gen, List.of(evidence), true)),
                    MemoryRecallSettings::defaults, mapper, executor);
            var result = service.recall(new MemoryRecallRequest(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0), MemoryRecallQuery.all(),
                    MemoryRecallRequest.Use.TOOL, null)).toCompletableFuture().join();
            assertEquals(new ObjectMapper().writeValueAsString(List.of(evidence)).length(), result.characters()); assertFalse(executor.isShutdown());
        }
    }
}
