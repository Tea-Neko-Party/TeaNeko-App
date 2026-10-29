package org.zexnocs.teanekoagent.memory.fact;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent._app_config.MemoryFactConfig;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证事实内容冻结、主语/来源边界、事件时间估算与持久化完成语义。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryFactContractTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");

    /**
     * 构造有明确来源的最小追加意图。
     * @return 固定动作、主语和来源
     */
    private MemoryFactAppend command() {
        var scope = new AgentScope("客户端", "群"); var subject = UUID.randomUUID();
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(subject), "平台用户", "消息", "目标");
        return new MemoryFactAppend(UUID.randomUUID(), new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0), subject,
                new MemoryFactValue(MemoryFactValue.Kind.FACT, "明确事实", List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                new MemoryFactSource(UUID.randomUUID(), "工具调用", actor));
    }

    /** 验证标签防御性冻结和稳定顺序，调用方修改列表不会改变已固定事实。 */
    @Test void contentFreezesCanonicalTags() {
        var tags = new ArrayList<>(List.of("乙", "甲", "甲"));
        var value = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, "喜欢茶", tags, -0.0, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
        tags.clear(); assertEquals(List.of("乙", "甲"), value.tags()); assertEquals(0.0, value.confidence());
        assertThrows(UnsupportedOperationException.class, () -> value.tags().add("外部修改"));
    }

    /** 验证无效置信度、过长正文和标签不能越过领域参数边界。 */
    @Test void invalidContentIsRejectedBeforeStorage() {
        for (var confidence : List.of(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            assertThrows(IllegalArgumentException.class, () -> new MemoryFactValue(MemoryFactValue.Kind.FACT, "事实", List.of(), confidence,
                    MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null));
        }
        assertThrows(IllegalArgumentException.class, () -> new MemoryFactValue(MemoryFactValue.Kind.FACT, " ", List.of(), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryFactValue(MemoryFactValue.Kind.FACT, "字".repeat(4097), List.of(), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryFactValue(MemoryFactValue.Kind.FACT, "事实", List.of(" "), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null));
    }

    /** 验证跨午夜闭区间交集，不受宿主默认时区或记录时刻影响。 */
    @Test void eventRangeUsesClosedInstantBoundaries() {
        var start = Instant.parse("2026-10-28T15:59:00Z"); var end = start.plusSeconds(120);
        var event = new MemoryEventTime(start, end, MemoryEventTime.Precision.MINUTE, "午夜前后");
        assertTrue(event.overlaps(end, end.plusSeconds(1), now, false));
        assertFalse(event.overlaps(end.plusNanos(1), null, now, false));
        assertTrue(new MemoryEventTime(null, start, MemoryEventTime.Precision.EXACT, "端点").overlaps(start, start, now, false));
    }

    /** 验证未知事件只在显式允许时回退记录时间，严格事件查询不会把估算当精确时间。 */
    @Test void unknownEventRequiresExplicitEstimatedFallback() {
        assertTrue(MemoryEventTime.unknown().overlaps(now, now, now, true));
        assertFalse(MemoryEventTime.unknown().overlaps(null, null, now, false));
        assertFalse(MemoryEventTime.unknown().overlaps(now.plusNanos(1), null, now, true));
    }

    /** 验证倒置范围、无端点精确时间及倒置查询不能被静默修正。 */
    @Test void invalidTimeSemanticsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryEventTime(now, now.minusNanos(1), MemoryEventTime.Precision.EXACT, "错误范围"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryEventTime(null, null, MemoryEventTime.Precision.DAY, "未知日期"));
        assertThrows(IllegalArgumentException.class, () -> MemoryEventTime.unknown().overlaps(now, now.minusNanos(1), now, true));
    }

    /** 验证固定引用不能借另一 scope 的来源发起写入，主语仍可独立于可信操作者保存。 */
    @Test void sourceScopeCannotReplaceBoundReference() {
        var value = command(); var actor = value.source().actor();
        var foreign = new AgentActorContext(new AgentScope("另一客户端", "群"), actor.origin(), actor.userId(), actor.platformUserId(), actor.sourceId(), actor.targetId());
        assertThrows(IllegalArgumentException.class, () -> new MemoryFactAppend(value.operationId(), value.reference(), value.subjectId(), value.value(),
                new MemoryFactSource(value.source().requestId(), value.source().actionId(), foreign)));
        assertNotEquals(actor.userId().orElseThrow(), new MemoryFactAppend(value.operationId(), value.reference(), UUID.randomUUID(), value.value(), value.source()).subjectId());
    }

    /** 验证有效期采用明确闭合边界，记录更新时刻不能早于创建时刻。 */
    @Test void expirationAndRecordOrderingRemainExplicit() {
        var command = command(); var fact = command.value();
        var value = new MemoryFactValue(fact.kind(), fact.text(), fact.tags(), fact.confidence(), fact.importance(), fact.eventTime(), now);
        var record = new MemoryFactRecord(command.operationId(), MemoryNamespaceId.SHARED, 0, 0, command.subjectId(), value, command.source(), now, now, false);
        assertFalse(record.isExpired(now.minusNanos(1))); assertTrue(record.isExpired(now));
        assertThrows(IllegalArgumentException.class, () -> new MemoryFactRecord(command.operationId(), MemoryNamespaceId.SHARED, 0, 0,
                command.subjectId(), value, command.source(), now, now.minusNanos(1), false));
    }

    /** 验证业务装配无 I/O，追加只固定时刻并等待原事务阶段，不提前返回成功。 */
    @Test void serviceWaitsForActualStoreCompletion() {
        var store = mock(IMemoryFactStore.class); new MemoryFactConfig().memoryFacts(store); verifyNoInteractions(store);
        var command = command(); var pending = new CompletableFuture<MemoryFactReceipt>(); when(store.append(command, now)).thenReturn(pending);
        var service = new MemoryFacts(store, Clock.fixed(now, ZoneOffset.UTC)); var result = service.append(command).toCompletableFuture(); assertFalse(result.isDone());
        pending.completeExceptionally(new IllegalStateException("测试事务回滚")); assertTrue(result.isCompletedExceptionally()); verify(store).append(command, now);
    }
}
