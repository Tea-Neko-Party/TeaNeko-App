package org.zexnocs.teanekoagent.runtime.monitor;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.zexnocs.teanekocore.event.AbstractEvent;
/**
 * 本地阈值触发的脱敏用量告警，仅供监控消费，不产生聊天通知或执行权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public final class AgentTokenAlertEvent extends AbstractEvent<AgentTokenAlertEvent.Alert> {
    /**
     * 固定本次脱敏阈值告警。
     * @param alert 不含原文的告警
     */
    public AgentTokenAlertEvent(Alert alert) { super(alert); }
    /**
     * 同 scope/实例冷却内只聚合次数，不保留业务正文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param report 当前轮次脱敏统计
     * @param threshold 本次本地阈值
     * @param suppressed 上个冷却期合并的告警数
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Alert(AgentTurnReport report, long threshold, long suppressed) { }
}
