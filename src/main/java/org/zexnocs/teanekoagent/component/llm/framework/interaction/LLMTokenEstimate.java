package org.zexnocs.teanekoagent.component.llm.framework.interaction;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 可导出的估算说明，不包含原始输入或运行句柄，也不承诺精确供应商计费。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param inputTokens 输入 token 估算
 * @param method 稳定的估算算法标识
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record LLMTokenEstimate(long inputTokens, String method) {
    /**
     * 拒绝负数及可能混入自由文本或敏感数据的算法标识。
     * @param inputTokens 非负估算值
     * @param method 小写字母、数字、点和连字符组成的算法标识
     * @throws IllegalArgumentException 当估算或标识非法时
     */
    public LLMTokenEstimate {
        if (inputTokens < 0 || method == null || !method.matches("[a-z0-9.-]{1,64}")) {
            throw new IllegalArgumentException("token 估算值或算法标识无效。");
        }
    }
}
