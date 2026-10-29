package org.zexnocs.teanekoagent.memory.recall;

/**
 * 每次查询固定的本地策略，不在查询中途重新读取配置。
 * @param injectionLimit 默认注入条数
 * @param toolLimit 默认工具查询条数
 * @param hardLimit 可显式请求的条数硬上限
 * @param maxCharacters 证据数组字符上限
 * @param maxScanned 原记录检查上限
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryRecallSettings(int injectionLimit, int toolLimit, int hardLimit, int maxCharacters, int maxScanned) {
    /**
     * 验证默认值与硬预算一致，执行绝对上限防止误配置成无界读取。
     * @param injectionLimit 注入数
     * @param toolLimit 工具数
     * @param hardLimit 硬上限
     * @param maxCharacters 字符上限
     * @param maxScanned 扫描上限
     * @throws IllegalArgumentException 当配置无效或默认值高于硬上限时
     */
    public MemoryRecallSettings {
        MemoryRecallLimits.validate(hardLimit, maxCharacters, maxScanned);
        if (injectionLimit < 1 || injectionLimit > hardLimit || toolLimit < 1 || toolLimit > hardLimit) {
            throw new IllegalArgumentException("记忆检索默认条数与硬上限不一致。");
        }
    }
    /**
     * 为无本地覆盖的可信宿主提供明确默认值。
     * @return 十二条注入、八条工具、二十条硬上限及有界扫描策略
     */
    public static MemoryRecallSettings defaults() { return new MemoryRecallSettings(12, 8, 20, 16000, 1000); }
    /**
     * 显式条数可高于用途默认值但不超过硬上限，其余预算只能收紧。
     * @param request 当前请求
     * @return 本次实际预算
     */
    public MemoryRecallLimits resolve(MemoryRecallRequest request) {
        var override = request.limits();
        return override == null ? new MemoryRecallLimits(request.use() == MemoryRecallRequest.Use.INJECTION ? injectionLimit : toolLimit, maxCharacters, maxScanned)
                : new MemoryRecallLimits(Math.min(override.maxResults(), hardLimit), Math.min(override.maxCharacters(), maxCharacters), Math.min(override.maxScanned(), maxScanned));
    }
}
