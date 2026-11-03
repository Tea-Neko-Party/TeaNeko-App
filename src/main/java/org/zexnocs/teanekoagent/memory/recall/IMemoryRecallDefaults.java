package org.zexnocs.teanekoagent.memory.recall;

/**
 * 本地检索策略来源，读取失败须明确失败而非换成宽松默认值。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IMemoryRecallDefaults {
    /**
     * 在本次业务执行域中取得一份完整策略。
     * @return 已验证策略
     */
    MemoryRecallSettings settings();
}
