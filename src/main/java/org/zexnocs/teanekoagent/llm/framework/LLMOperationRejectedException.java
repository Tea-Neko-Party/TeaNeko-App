package org.zexnocs.teanekoagent.llm.framework;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 宿主在动作边界拒绝继续交互；不同于可恢复工具错误，不执行余下工具或额外模型请求。
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
public final class LLMOperationRejectedException extends RuntimeException {
    /** 使用固定诊断，不携带身份、配置或原始异常。 */
    public LLMOperationRejectedException() { super("宿主已拒绝继续执行交互动作。"); }
}
