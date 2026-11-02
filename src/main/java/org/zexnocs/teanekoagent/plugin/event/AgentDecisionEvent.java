package org.zexnocs.teanekoagent.plugin.event;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.plugin.PluginScopedEvent;
import java.util.*;

/**
 * 同步有序的 Agent 决策草稿；宿主冻结后不可再修改，拒绝决定不可撤销。
 * @param <T> 允许编辑的业务项类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public abstract class AgentDecisionEvent<T> extends AbstractEvent<AgentEventContext> implements PluginScopedEvent {
    private List<T> values;
    private boolean frozen;
    private boolean denied;
    /**
     * 固定可信上下文并复制业务草稿。
     * @param context 可信定位
     * @param values 初始业务项
     */
    protected AgentDecisionEvent(AgentEventContext context, List<T> values) { super(context); this.values = List.copyOf(values); }
    /** @return 当前不可变业务项快照 */
    public synchronized List<T> values() { return values; }
    /**
     * 替换草稿，不可修改可信上下文；宿主随后进行原业务校验。
     * @param values 新的完整列表
     * @throws IllegalStateException 当事件已经冻结时
     */
    public synchronized void replace(List<T> values) {
        if (frozen) { throw new IllegalStateException("Agent 决策事件已经冻结。"); }
        this.values = List.copyOf(values);
    }
    /**
     * 宿主在通知结束或失败后冻结，迟到修改明确失败。
     * @return 最终草稿
     */
    public synchronized List<T> freeze() { frozen = true; return values; }
    /**
     * 禁止替换可信定位信息。
     * @param data 替换请求
     * @throws UnsupportedOperationException 总是抛出
     */
    @Override public final void setData(AgentEventContext data) { throw new UnsupportedOperationException("Agent 事件可信上下文不可替换。"); }
    /** @return 当前是否已拒绝 */
    @Override public synchronized boolean isCancelled() { return denied; }
    /**
     * 只允许增加拒绝决定。
     * @param cancelled 是否拒绝
     */
    @Override public synchronized void setCancelled(boolean cancelled) { if (cancelled) { denied = true; } }
    /**
     * 原子读取并增加拒绝决定。
     * @param cancelled 是否拒绝
     * @return 原决定
     */
    @Override public synchronized boolean getAndSetCancelled(boolean cancelled) { boolean old = denied; setCancelled(cancelled); return old; }
    /**
     * 仅通知本轮安装了能力的插件。
     * @param pluginId 插件标识
     * @return 是否允许参与
     */
    @Override public boolean acceptsPlugin(String pluginId) { return getData().features().stream().anyMatch(id -> id.startsWith(pluginId + ":")); }
    /** @return 决策事件始终禁止独立异步监听 */
    @Override public boolean allowsAsyncListeners() { return false; }
}
