package org.zexnocs.teanekoagent.llm.framework.input;

/**
 * 模型选项的解析策略，独立于供应商 metadata 和凭据；只影响本地参数准备。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public enum LLMOptionsPolicy {
    /** 合并适配器基础值、文件默认值与本次覆盖，保留既有调用行为。 */
    MERGE_DEFAULTS,
    /** 参数已由宿主解析；保留缺失值，不再补入文件或适配器默认值，仍校验输入和能力。 */
    RESOLVED
}
