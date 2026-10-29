package org.zexnocs.teanekoagent.memory.recall;

/**
 * 一次查询的有界工作量与证据输出预算；字符按实际证据数组 JSON 计量。
 * @param maxResults 最大结果数
 * @param maxCharacters 最大证据数组字符数，包含数组括号与逗号
 * @param maxScanned 最大检查记录数
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryRecallLimits(int maxResults, int maxCharacters, int maxScanned) {
    public static final int MAX_RESULTS = 100;
    public static final int MAX_CHARACTERS = 1_000_000;
    public static final int MAX_SCANNED = 10_000;
    /**
     * 拒绝无界扫描及不能容纳空数组的预算，不静默接受非法值。
     * @param maxResults 结果数
     * @param maxCharacters 字符数
     * @param maxScanned 检查记录数
     * @throws IllegalArgumentException 当任一预算超出执行范围时
     */
    public MemoryRecallLimits {
        validate(maxResults, maxCharacters, maxScanned);
    }
    /**
     * 为策略和单次预算共用执行范围校验，不创建仅用于校验的临时值对象。
     * @param maxResults 结果上限
     * @param maxCharacters 字符上限
     * @param maxScanned 扫描上限
     * @throws IllegalArgumentException 当任一值越界时
     */
    static void validate(int maxResults, int maxCharacters, int maxScanned) {
        if (maxResults < 1 || maxResults > MAX_RESULTS || maxCharacters < 2 || maxCharacters > MAX_CHARACTERS
                || maxScanned < 1 || maxScanned > MAX_SCANNED) { throw new IllegalArgumentException("记忆检索预算超出允许范围。"); }
    }
}
