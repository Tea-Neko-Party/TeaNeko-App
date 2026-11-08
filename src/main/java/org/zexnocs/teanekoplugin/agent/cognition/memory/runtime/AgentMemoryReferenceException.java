package org.zexnocs.teanekoplugin.agent.cognition.memory.runtime;

// 2026/11/07：通过中立生命周期端口接入宿主，认知类型和业务校验仍归插件。

import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextFailure;

import java.io.Serial;
import java.util.Objects;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 空间引用失效的非敏感类型化原因，不携带配置值、存储正文或原始异常链。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：停用说明覆盖可选组件及插件资格，不将其误报为整个 Agent 关闭。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public final class AgentMemoryReferenceException extends RuntimeException implements IAgentContextFailure {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * 构造固定中文失败原因，不允许调用方注入任意异常文本。
     * 2026/11/07：组件或插件停用共用类型化禁用原因。
     * @param reason 明确的引用失败类别
     * @throws NullPointerException 当失败原因为空时
     */
    public AgentMemoryReferenceException(Reason reason) {
        super(switch (Objects.requireNonNull(reason, "空间引用失败原因不能为空。")) {
            case CHANGED -> "记忆空间引用或数据代次已变化。";
            case DISABLED -> "当前作用域的记忆能力未启用，不能访问记忆空间。";
            case UNAVAILABLE -> "记忆空间引用读取或校验不可用。";
        });
        this.reason = reason;
    }

    /**
     * 提供稳定分类，不让上层解析异常文本判断业务状态。
     * @return 明确原因
     */
    public Reason reason() { return reason; }

    /**
     * 分开表达引用已变更、scope 已停用与基础设施不可用。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    public enum Reason { CHANGED, DISABLED, UNAVAILABLE }

    /** @return 供宿主终止当前轮次的固定状态，不解析异常正文 */
    @Override
    public AgentRunResult.Status rejectionStatus() {
        return switch (reason) {
            case CHANGED -> AgentRunResult.Status.STALE_CONVERSATION;
            case DISABLED -> AgentRunResult.Status.SKIPPED;
            case UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
        };
    }

}
