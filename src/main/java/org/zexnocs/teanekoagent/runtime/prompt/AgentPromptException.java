package org.zexnocs.teanekoagent.runtime.prompt;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.io.Serial;
import java.util.Objects;

/**
 * Prompt 资料端口的固定失败类别，不导出源正文、权限数据或原异常链。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public final class AgentPromptException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * 使用固定中文原因报告禁用、失效、不可用或完整原输入超限。
     * @param reason 明确失败类别
     * @throws NullPointerException 当类别为空时
     */
    public AgentPromptException(Reason reason) {
        super(switch (Objects.requireNonNull(reason, "Prompt 资料失败类别不能为空。")) {
            case DISABLED -> "当前参考资料功能已关闭。";
            case CHANGED -> "当前参考资料的绑定或代次已变化。";
            case UNAVAILABLE -> "当前参考资料暂不可用。";
            case INPUT_TOO_LARGE -> "本次原始输入超过完整 Prompt 的字节预算。";
        });
        this.reason = reason;
    }

    /**
     * 提供固定类别，不要求上层解析异常文本。
     * @return 明确失败类别
     */
    public Reason reason() { return reason; }

    /**
     * 分离功能关闭、绑定变化、来源故障及原始输入超限。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Reason { DISABLED, CHANGED, UNAVAILABLE, INPUT_TOO_LARGE }
}
