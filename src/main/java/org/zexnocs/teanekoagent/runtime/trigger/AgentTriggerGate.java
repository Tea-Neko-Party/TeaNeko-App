package org.zexnocs.teanekoagent.runtime.trigger;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

/**
 * 自动触发的有界准入与交付记账；只保存纯状态，不调用模型、Timer 或平台发送器。
 * 每个 scope 同时只借出一个六十秒票据，副作用前须 verify，结束后须 finish。
 * <br>2026/11/02：原生私聊作为直接对话信号，不依赖无法产生的提及。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：说明时区解析仅验证格式，不因校验而改写原始时区标识。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public final class AgentTriggerGate {
    private final Function<AgentScope, Settings> settings;
    private final Clock clock;
    private final Map<AgentScope, State> scopes = new LinkedHashMap<>();

    /**
     * 使用现有配置快照端口和显式时钟，不创建任务或新执行线程。
     * @param settings 当前可信配置读取
     * @param clock 显式时钟
     */
    public AgentTriggerGate(Function<AgentScope, Settings> settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings); this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 原生适配器确定信号；普通消息不因输入正文声明“提及机器人”而获得信号。
     * @param signal 宿主固定的原生信号
     * @return 可执行票据；未登记、禁用、旁观、静默、冷却或容量不足时为空
     */
    public Optional<Ticket> acquire(Signal signal) {
        var policy = settings.apply(signal.actor().scope()); var now = clock.instant();
        if (!policy.scope().registered() || !policy.scope().enabled() || signal.botAuthored()
                || signal.kind() == Kind.MESSAGE && signal.actor().origin() != AgentActorContext.Origin.USER) { return Optional.empty(); }
        synchronized (scopes) {
            scopes.entrySet().removeIf(entry -> entry.getValue().unusedBefore(now));
            var state = scopes.get(signal.actor().scope());
            if (state == null) {
                if (scopes.size() >= 1000) { return Optional.empty(); }
                state = new State(); scopes.put(signal.actor().scope(), state);
            }
            state.recent.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minusSeconds(300)) || entry.getValue().isAfter(now));
            if (state.pending != null && !state.pending.expiresAt().isAfter(now)) { state.pending = null; }
            if (state.pending != null) { return Optional.empty(); }
            boolean proactive = signal.kind() != Kind.MESSAGE;
            if (proactive) {
                if (policy.scope().mode() != AgentScopePolicy.Mode.ACTIVE || quiet(policy, now)
                        || state.lastProactive != null && now.isBefore(state.lastProactive.plusSeconds(policy.cooldownSeconds()))) { return Optional.empty(); }
            } else {
                var user = signal.actor().userId().orElseThrow();
                // 2026/11/02：私聊由宿主消息类型确认，仍经过登记、启用和占用检查。
                boolean direct = signal.privateConversation() || signal.mentionsBot() || signal.repliesToBot();
                boolean contextual = policy.scope().mode() != AgentScopePolicy.Mode.PASSIVE_BASIC
                        && (signal.namesBot() || state.recent.containsKey(user));
                if (!direct && !contextual || state.recent.size() >= 256 && !state.recent.containsKey(user)) { return Optional.empty(); }
            }
            var ticket = new Ticket(UUID.randomUUID(), signal, policy, now.plusSeconds(60)); state.pending = ticket;
            return Optional.of(ticket);
        }
    }

    /**
     * 在模型、提交及实际发送前重新查询当前开关，过期或配置已变化的票据不能沿用。
     * @param ticket 已取得的原票据
     * @throws IllegalStateException 当票据过期、来源或配置失效时
     */
    public void verify(Ticket ticket) {
        var current = settings.apply(ticket.signal().actor().scope()); var now = clock.instant();
        synchronized (scopes) { requireCurrent(ticket, current, now); }
    }

    /**
     * 仅实际交付成功才延长该发起者续聊窗口或记录主动发言冷却，失败释放占用。
     * @param ticket 原票据
     * @param delivered 是否已获得真实交付成功
     * @throws IllegalStateException 当成功记账使用过期或失效票据时
     */
    public void finish(Ticket ticket, boolean delivered) {
        Settings current;
        try { current = delivered ? settings.apply(ticket.signal().actor().scope()) : ticket.settings(); }
        catch (RuntimeException failure) { finish(ticket, false); throw failure; }
        var now = clock.instant();
        synchronized (scopes) {
            var state = scopes.get(ticket.signal().actor().scope());
            if (state == null || !ticket.equals(state.pending)) {
                if (delivered) { throw new IllegalStateException("自动触发票据已失效。"); } return;
            }
            try {
                if (delivered) {
                    if (ticket.signal().kind() != Kind.MESSAGE) { state.lastProactive = now; }
                    requireCurrent(ticket, current, now);
                    if (ticket.signal().kind() == Kind.MESSAGE) { state.recent.put(ticket.signal().actor().userId().orElseThrow(), now); }
                }
            } finally { state.pending = null; }
        }
    }

    /**
     * 仅在持有状态锁时验证票据，不接受另一个票据或调用方拼接的配置快照。
     * @param ticket 原票据
     * @param current 当前可信配置
     * @param now 当前时刻
     * @throws IllegalStateException 当票据不可继续时
     */
    private void requireCurrent(Ticket ticket, Settings current, Instant now) {
        var state = scopes.get(ticket.signal().actor().scope());
        if (state == null || !ticket.equals(state.pending) || !ticket.expiresAt().isAfter(now)
                || !ticket.settings().equals(current) || !current.scope().registered() || !current.scope().enabled()
                || ticket.signal().kind() != Kind.MESSAGE && quiet(current, now)) {
            throw new IllegalStateException("自动触发已取消、过期或当前策略已变化。");
        }
    }

    /**
     * 夜间跨日区间使用显式时区；起止相同代表关闭静默，不影响被动直接回复。
     * @param policy 当前配置
     * @param now 当前时刻
     * @return 是否位于主动静默时段
     */
    private static boolean quiet(Settings policy, Instant now) {
        int hour = now.atZone(ZoneId.of(policy.zone())).getHour(); int start = policy.quietStartHour(), end = policy.quietEndHour();
        return start != end && (start < end ? hour >= start && hour < end : hour >= start || hour < end);
    }

    /**
     * scope 的进程内瞬态记账，不保存客户端、模型状态或原文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    private static final class State {
        private final Map<UUID, Instant> recent = new HashMap<>();
        private @Nullable Instant lastProactive;
        private @Nullable Ticket pending;
        /**
         * 安全回收不再影响任何窗口的 scope，不淘汰有效票据及冷却。
         * @param now 当前时刻
         * @return 状态是否可回收
         */
        private boolean unusedBefore(Instant now) {
            return (pending == null || !pending.expiresAt().isAfter(now))
                    && (lastProactive == null || lastProactive.isBefore(now.minusSeconds(86400)))
                    && recent.values().stream().allMatch(time -> time.isBefore(now.minusSeconds(300)));
        }
    }

    /**
     * 与平台无关的当前触发设置，mode 继续沿用原 Agent scope 策略。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param scope 当前登记、启用与级别
     * @param zone 静默规则的明确时区
     * @param cooldownSeconds 主动发言冷却，一秒至一天
     * @param quietStartHour 静默起点小时
     * @param quietEndHour 静默终点小时
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Settings(AgentScopePolicy scope, String zone, long cooldownSeconds, int quietStartHour, int quietEndHour) {
        /**
         * 非法配置拒绝，不回退服务器时区或关闭冷却。
         * @param scope 当前策略
         * @param zone 时区
         * @param cooldownSeconds 冷却
         * @param quietStartHour 静默起点
         * @param quietEndHour 静默终点
         */
        public Settings {
            Objects.requireNonNull(scope);
            // 2026/11/05：构造时验证时区，但保留调用者原始标识，不将别名自动规范化。
            //noinspection ResultOfMethodCallIgnored
            ZoneId.of(zone);
            if (cooldownSeconds < 1 || cooldownSeconds > 86400 || quietStartHour < 0 || quietStartHour > 23 || quietEndHour < 0 || quietEndHour > 23) {
                throw new IllegalArgumentException("自动触发冷却或静默时段不合法。");
            }
        }
    }

    /**
     * 原生适配器确定的信号，不从 LLM 工具参数推导。
     * <br>2026/11/02：增加私聊直接触发标记，旧宿主默认按群聊信号处理。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param actor 实际来源，定时信号可使用无用户身份的 SYSTEM
     * @param kind 消息、定时或接话候选
     * @param mentionsBot 原生提及机器人
     * @param repliesToBot 原生引用机器人
     * @param namesBot 命中宿主明确配置的称呼
     * @param botAuthored 是否机器人自己发出的消息
     * @param privateConversation 宿主确认的原生私聊，不能从正文推导
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Signal(AgentActorContext actor, Kind kind, boolean mentionsBot, boolean repliesToBot, boolean namesBot, boolean botAuthored, boolean privateConversation) {
        /**
         * 2026/11/02：保留原生群聊及自主信号构造方式，不隐式扩大触发范围。
         * @param actor 实际来源
         * @param kind 信号类别
         * @param mentionsBot 提及
         * @param repliesToBot 引用
         * @param namesBot 称呼
         * @param botAuthored 自发消息
         */
        public Signal(AgentActorContext actor, Kind kind, boolean mentionsBot, boolean repliesToBot, boolean namesBot, boolean botAuthored) {
            this(actor, kind, mentionsBot, repliesToBot, namesBot, botAuthored, false);
        }
        /**
         * 信号缺失不自动解释为普通用户消息。
         * @param actor 实际来源
         * @param kind 信号类型
         * @param mentionsBot 提及
         * @param repliesToBot 引用
         * @param namesBot 称呼
         * @param botAuthored 自发消息
         * @param privateConversation 原生私聊
         */
        public Signal { Objects.requireNonNull(actor); Objects.requireNonNull(kind); }
    }

    /**
     * 有界纯数据票据，不包含控制句柄或服务引用。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 唯一占用标识
     * @param signal 原生信号
     * @param settings 当时的固定配置
     * @param expiresAt 最后有效时刻
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Ticket(UUID id, Signal signal, Settings settings, Instant expiresAt) { }

    /**
     * 触发来源与具体要执行的 Agent 活动分离，不为候选直接启动模型。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Kind { MESSAGE, TIMER, INTERJECTION }
}
