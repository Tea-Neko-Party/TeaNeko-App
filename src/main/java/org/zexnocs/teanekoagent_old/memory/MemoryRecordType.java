package org.zexnocs.teanekoagent_old.memory;

/**
 * 第一阶段确定性记忆层使用的记忆类型。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public enum MemoryRecordType {
    /**
     * 用户长期事实。
     */
    USER_FACT,

    /**
     * 用户偏好。
     */
    PREFERENCE,

    /**
     * 关系状态。
     */
    RELATIONSHIP,

    /**
     * 性格学习修正。
     */
    PERSONALITY_DELTA,

    /**
     * 片段或会话摘要。
     */
    EPISODE_SUMMARY
}
