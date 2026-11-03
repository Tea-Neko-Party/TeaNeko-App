package org.zexnocs.teanekoagent.agent.memory;

import java.io.Serial;
import java.util.Objects;

/**
 * 空间引用失效的非敏感类型化原因，不携带配置值、存储正文或原始异常链。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public final class AgentMemoryReferenceException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * 构造固定中文失败原因，不允许调用方注入任意异常文本。
     * @param reason 明确的引用失败类别
     * @throws NullPointerException 当失败原因为空时
     */
    public AgentMemoryReferenceException(Reason reason) {
        super(switch (Objects.requireNonNull(reason, "空间引用失败原因不能为空。")) {
            case CHANGED -> "记忆空间引用或数据代次已变化。";
            case DISABLED -> "当前作用域未启用 Agent，不能访问记忆空间。";
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
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public enum Reason { CHANGED, DISABLED, UNAVAILABLE }
}
