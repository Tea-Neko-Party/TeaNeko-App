package org.zexnocs.teanekoagent.adapter.platform;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentActivity;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.access.IAgentActivityPolicy;
import org.zexnocs.teanekoagent.agent.trigger.AgentTriggerGate;

import java.util.HashMap;
import java.util.Map;

/**
 * 将自动触发票据接入原 Agent 门控，租约仅存在宿主边界，不进入组件或 Prompt。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Service
public final class AgentAutomaticAccess implements IAgentActivityPolicy {
    private final AgentTriggerGate triggers;
    private final Map<AgentActorContext, Lease> tickets = new HashMap<>();
    /**
     * 保存原触发门控，不启动活动。
     * @param triggers 当前触发策略
     */
    public AgentAutomaticAccess(AgentTriggerGate triggers) { this.triggers = triggers; }

    /**
     * 取得唯一来源租约，释放前所有模型、工具及提交检查都复核原票据。
     * @param ticket 原票据
     * @throws IllegalStateException 当重复绑定或容量不足时
     */
    public void bind(AgentTriggerGate.Ticket ticket) {
        bind(ticket, () -> { });
    }

    /**
     * 自主活动额外复核当前动作开关及固定目标，所有检查仍在原业务域执行。
     * @param ticket 原触发票据
     * @param guard 宿主当前目标和开关复核，不保存到组件
     */
    public void bind(AgentTriggerGate.Ticket ticket, Runnable guard) {
        triggers.verify(ticket); var actor = ticket.signal().actor();
        guard.run();
        synchronized (tickets) {
            if (tickets.size() >= 1000 || tickets.containsKey(actor)) { throw new IllegalStateException("自动请求来源已占用或容量不足。"); }
            tickets.put(actor, new Lease(ticket, guard));
        }
    }

    /**
     * 按原票据释放，迟到完成不移除新的拥有者。
     * @param ticket 原票据
     */
    public void release(AgentTriggerGate.Ticket ticket) {
        synchronized (tickets) { var lease = tickets.get(ticket.signal().actor()); if (lease != null && lease.ticket().equals(ticket)) { tickets.remove(ticket.signal().actor()); } }
    }

    /**
     * 自动回复和自主活动无有效租约时拒绝；已有租约的派生活动同样复核。
     * @param actor 真实来源
     * @param activity 当前实际活动
     * @return 当前仍可继续时为真
     */
    @Override public boolean allows(AgentActorContext actor, AgentActivity activity) {
        Lease lease; synchronized (tickets) { lease = tickets.get(actor); }
        if (lease == null) { return activity != AgentActivity.AUTOMATIC_REPLY && activity != AgentActivity.PROACTIVE; }
        triggers.verify(lease.ticket()); lease.guard().run(); synchronized (tickets) { return lease.equals(tickets.get(actor)); }
    }

    /**
     * 宿主短暂拥有的运行边界，不序列化、不进入组件或 Prompt。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param ticket 原票据
     * @param guard 当前宿主约束
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    private record Lease(AgentTriggerGate.Ticket ticket, Runnable guard) { }
}
