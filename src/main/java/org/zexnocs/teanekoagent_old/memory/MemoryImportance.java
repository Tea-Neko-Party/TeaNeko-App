package org.zexnocs.teanekoagent_old.memory;

/**
 * Agent 记忆重要度。
 * <br>重要度越高，写入方越应保留精确时间、来源和上下文信息。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public enum MemoryImportance {
    /**
     * 低重要度，可只保留大致时间范围。
     */
    LOW,

    /**
     * 普通重要度。
     */
    NORMAL,

    /**
     * 高重要度，应尽量保留精确时间点或较窄时间范围。
     */
    HIGH,

    /**
     * 关键记忆，应保留可获得的最详细时间和来源。
     */
    CRITICAL
}
