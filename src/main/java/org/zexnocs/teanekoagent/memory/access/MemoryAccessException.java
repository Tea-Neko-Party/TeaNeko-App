package org.zexnocs.teanekoagent.memory.access;

import java.io.Serial;
import java.util.Objects;

/**
 * 记忆访问的固定中文失败，不携带原始消息、配置或基础设施异常链。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryAccessException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * 只允许稳定类别进入工具或宿主反馈。
     * @param reason 拒绝或失败类别
     * @throws NullPointerException 当类别为空时
     */
    public MemoryAccessException(Reason reason) {
        super(switch (Objects.requireNonNull(reason, "记忆访问失败类别不能为空。")) {
            case DISABLED -> "当前作用域未启用 Agent，不能访问记忆。";
            case READ_DISABLED -> "当前作用域已关闭记忆读取。";
            case ADD_DISABLED -> "当前作用域已关闭明确新增记忆。";
            case INTENT_REQUIRED -> "缺少与原请求和原文一致的明确新增意图。";
            case CONFIGURATION_ERROR -> "记忆访问策略配置不可用。";
            case STOPPED -> "本次记忆访问已停止，已开始的写入仍需核对提交结果。";
            case TIMED_OUT -> "本次记忆访问已超时，已开始的写入仍需核对提交结果。";
            case UNAVAILABLE -> "记忆访问暂不可用。";
        });
        this.reason = reason;
    }

    /**
     * 提供稳定类别，避免调用端解析异常文本。
     * @return 固定失败类别
     */
    public Reason reason() { return reason; }

    /**
     * 分开表达功能关闭、明确意图缺失、停止和基础设施故障。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Reason { DISABLED, READ_DISABLED, ADD_DISABLED, INTENT_REQUIRED, CONFIGURATION_ERROR, STOPPED, TIMED_OUT, UNAVAILABLE }
}
