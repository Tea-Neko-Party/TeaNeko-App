package org.zexnocs.teanekoagent.llm.framework.input;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 模型选项的解析策略，独立于供应商 metadata 和凭据；只影响本地参数准备。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public enum LLMOptionsPolicy {
    /** 合并适配器基础值、文件默认值与本次覆盖，保留既有调用行为。 */
    MERGE_DEFAULTS,
    /** 参数已由宿主解析；保留缺失值，不再补入文件或适配器默认值，仍校验输入和能力。 */
    RESOLVED
}
