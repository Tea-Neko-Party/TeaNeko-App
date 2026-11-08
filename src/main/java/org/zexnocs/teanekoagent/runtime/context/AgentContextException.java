package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.runtime.AgentRunResult;

import java.io.Serial;
import java.util.Objects;

/**
 * 宿主上下文交接失败的固定分类，不包含插件数据或存储内容。
 * <br>2026/11/07：统一处理上下文缺失、失效和停用，业务细节由提供者验证。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class AgentContextException extends RuntimeException implements IAgentContextFailure {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * @param reason 固定的上下文失效类别
     * @throws NullPointerException 当类别为空时
     */
    public AgentContextException(Reason reason) {
        super(switch (Objects.requireNonNull(reason, "上下文失败类别不能为空。")) {
            case CHANGED -> "当前上下文绑定或版本已经变化。";
            case DISABLED -> "当前上下文提供能力已经停用。";
            case UNAVAILABLE -> "当前上下文无法读取或校验。";
        });
        this.reason = reason;
    }

    /** @return 固定的失败类别 */
    public Reason reason() {
        return reason;
    }

    /** @return 终止当前请求的业务状态 */
    @Override
    public AgentRunResult.Status rejectionStatus() {
        return switch (reason) {
            case CHANGED -> AgentRunResult.Status.STALE_CONVERSATION;
            case DISABLED -> AgentRunResult.Status.SKIPPED;
            case UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
        };
    }

    /**
     * 只描述宿主可处理的上下文终止方式。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public enum Reason { CHANGED, DISABLED, UNAVAILABLE }
}
