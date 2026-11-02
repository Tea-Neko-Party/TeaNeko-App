package org.zexnocs.teanekoagent.agent.trigger;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 只覆盖分级、并发占用、实际交付窗口及主动静默冷却等行为边界。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentTriggerGateTest {
    /** 最低被动不续聊，中级仅在同人成功交付后五分钟内续聊，旁观和失效票据不能借用。 */
    @Test void passiveLevelsUseOnlySuccessfulSameActorContinuation() {
        var now = new AtomicReference<>(Instant.parse("2026-11-02T02:00:00Z"));
        var settings = new AtomicReference<>(settings(AgentScopePolicy.Mode.PASSIVE_BASIC)); var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get()); var gate = new AgentTriggerGate(scope -> settings.get(), clock);
        var actor = actor(); var plain = signal(actor, false, false); var named = signal(actor, false, true);
        assertTrue(gate.acquire(named).isEmpty());
        var direct = gate.acquire(signal(actor, true, false)).orElseThrow(); assertTrue(gate.acquire(plain).isEmpty());
        gate.finish(direct, true); assertTrue(gate.acquire(plain).isEmpty());
        settings.set(settings(AgentScopePolicy.Mode.PASSIVE_CONTEXTUAL));
        var continuation = gate.acquire(plain).orElseThrow(); gate.finish(continuation, false);
        var other = new AgentActorContext(actor.scope(), actor.origin(), Optional.of(UUID.randomUUID()), "other", "m2", "target");
        assertTrue(gate.acquire(signal(other, false, false)).isEmpty());
        now.set(now.get().plusSeconds(301)); assertTrue(gate.acquire(plain).isEmpty());
        var current = gate.acquire(named).orElseThrow();
        settings.set(new AgentTriggerGate.Settings(AgentScopePolicy.unregistered(), "Asia/Shanghai", 900, 23, 7));
        assertThrows(IllegalStateException.class, () -> gate.verify(current)); gate.finish(current, false);
        assertTrue(gate.acquire(signal(actor, true, false)).isEmpty());
    }

    /** 主动候选夜间静默且不能并发穿透，成功发送后冷却；过期票据不影响新拥有者。 */
    @Test void proactiveQuietCooldownAndExpiredTicketsAreFenced() {
        var now = new AtomicReference<>(Instant.parse("2026-11-02T14:59:00Z")); var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get()); var gate = new AgentTriggerGate(scope -> settings(AgentScopePolicy.Mode.ACTIVE), clock);
        var user = actor(); var system = new AgentActorContext(user.scope(), AgentActorContext.Origin.SYSTEM, Optional.empty(), "", "timer", "target");
        var signal = new AgentTriggerGate.Signal(system, AgentTriggerGate.Kind.TIMER, false, false, false, false);
        var first = gate.acquire(signal).orElseThrow(); assertTrue(gate.acquire(signal).isEmpty()); gate.finish(first, true);
        assertTrue(gate.acquire(signal).isEmpty()); now.set(Instant.parse("2026-11-02T15:30:00Z"));
        assertTrue(gate.acquire(signal).isEmpty());
        var passive = gate.acquire(signal(user, true, false)).orElseThrow(); gate.finish(passive, false);
        now.set(Instant.parse("2026-11-02T23:00:00Z")); var expired = gate.acquire(signal).orElseThrow();
        now.set(now.get().plusSeconds(61)); var replacement = gate.acquire(signal).orElseThrow();
        assertThrows(IllegalStateException.class, () -> gate.verify(expired)); gate.finish(expired, false);
        gate.verify(replacement); gate.finish(replacement, true);
        assertTrue(gate.acquire(new AgentTriggerGate.Signal(user, AgentTriggerGate.Kind.INTERJECTION, false, false, false, true)).isEmpty());
    }

    /**
     * 构造明确时区和默认冷却的当前配置。
     * @param mode 当前级别
     * @return 不含平台依赖的配置
     */
    private static AgentTriggerGate.Settings settings(AgentScopePolicy.Mode mode) {
        return new AgentTriggerGate.Settings(new AgentScopePolicy(true, true, mode), "Asia/Shanghai", 900, 23, 7);
    }

    /**
     * 使用独立 UUID 固定真实用户来源。
     * @return 当前原生身份
     */
    private static AgentActorContext actor() {
        return new AgentActorContext(new AgentScope("client", "group"), AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "user", "m", "target");
    }

    /**
     * 由宿主固定信号，不使用文本声明生成提及标志。
     * @param actor 原生身份
     * @param mention 原生提及
     * @param name 明确称呼
     * @return 消息触发候选
     */
    private static AgentTriggerGate.Signal signal(AgentActorContext actor, boolean mention, boolean name) {
        return new AgentTriggerGate.Signal(actor, AgentTriggerGate.Kind.MESSAGE, mention, false, name, false);
    }
}
