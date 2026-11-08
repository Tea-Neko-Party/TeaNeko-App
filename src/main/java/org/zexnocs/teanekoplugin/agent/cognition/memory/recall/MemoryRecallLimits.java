package org.zexnocs.teanekoplugin.agent.cognition.memory.recall;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 一次查询的有界工作量与证据输出预算；字符按实际证据数组 JSON 计量。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param maxResults 最大结果数
 * @param maxCharacters 最大证据数组字符数，包含数组括号与逗号
 * @param maxScanned 最大检查记录数
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
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
