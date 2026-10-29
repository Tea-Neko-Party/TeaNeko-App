package org.zexnocs.teanekoagent.memory.recall;

import java.util.Objects;

/**
 * 固定原因的检索失败，不将配置、数据库或原始正文放进异常链。
 * namespace 缺失和代次变化沿用独立的 MemoryFactGenerationException。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryRecallException extends RuntimeException {
    private final Reason reason;
    /**
     * 仅从已知分类生成中文信息，不接受任意基础设施文本。
     * @param reason 失败类别
     * @throws NullPointerException 当类别为空时
     */
    public MemoryRecallException(Reason reason) {
        super(Objects.requireNonNull(reason, "检索失败类别不能为空。") == Reason.CONFIGURATION_ERROR ? "记忆检索配置无效。" : "记忆检索来源暂不可用。");
        this.reason = reason;
    }
    /**
     * 供调用方明确区分配置错误和暂不可用，不依赖中文信息解析。
     * @return 固定失败类别
     */
    public Reason reason() { return reason; }
    /**
     * 正常无命中不属于失败，失败不能触发伪造的空结果降级。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Reason { CONFIGURATION_ERROR, UNAVAILABLE }
}
