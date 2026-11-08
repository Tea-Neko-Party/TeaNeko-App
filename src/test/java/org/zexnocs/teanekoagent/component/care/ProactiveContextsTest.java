package org.zexnocs.teanekoagent.component.care;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.social.proactive.IProactiveContextSource;
import org.zexnocs.teanekoplugin.agent.social.proactive.ProactiveContexts;
import org.zexnocs.teanekoplugin.agent.social.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 主动组件组合失败时仍释放已准备的候选，不因新增扩展而遗留占用。
 * <br>2026/11/06：覆盖同步资格失败及异步收尾失败，未执行真实模型和发送。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Timeout(10)
class ProactiveContextsTest {
    /** 后一个扩展读取资格失败时，先前会话按未进入发送的弃言释放一次。 */
    @Test void releasesPreparedSessionsWhenTheNextSourceThrows() {
        try (var control = ExecutionControl.create()) {
            var reference = new MemoryNamespaceReference(new AgentScope("test", "private"), MemoryNamespaceId.SHARED, 0, 0);
            var target = new ProactiveTargets.Target(reference.scope(), "user", false, "bot");
            var first = mock(IProactiveContextSource.class);
            var second = mock(IProactiveContextSource.class);
            var session = mock(IProactiveContextSource.ISession.class);
            when(first.applies(any(), any())).thenReturn(true);
            when(first.prepare(any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(Optional.of(session)));
            when(session.finish(Delivery.ABSTAINED)).thenReturn(CompletableFuture.completedStage(null));
            when(second.applies(any(), any())).thenThrow(new IllegalStateException("测试资格查询失败"));
            var contexts = new ProactiveContexts(List.of(first, second));
            assertThrows(CompletionException.class, () -> contexts.prepare(target, AgentTriggerGate.Kind.TIMER,
                    reference, UUID.randomUUID(), control).toCompletableFuture().join());
            verify(session).finish(Delivery.ABSTAINED);
        }
    }

    /** 单个会话收尾失败仍通知后续会话，最终保留原失败而不是报告成功。 */
    @Test void finishesRemainingSessionsAfterOneFails() {
        var first = mock(IProactiveContextSource.ISession.class);
        var second = mock(IProactiveContextSource.ISession.class);
        when(first.finish(Delivery.SENT)).thenReturn(CompletableFuture.failedStage(new IllegalStateException("测试提交失败")));
        when(second.finish(Delivery.SENT)).thenReturn(CompletableFuture.completedStage(null));
        var selection = new ProactiveContexts.Selection(true, List.of(first, second));
        assertThrows(CompletionException.class, () -> selection.finish(Delivery.SENT).toCompletableFuture().join());
        verify(second).finish(Delivery.SENT);
    }
}
