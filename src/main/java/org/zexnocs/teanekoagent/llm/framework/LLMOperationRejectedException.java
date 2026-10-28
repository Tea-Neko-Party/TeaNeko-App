package org.zexnocs.teanekoagent.llm.framework;

/**
 * 宿主在动作边界拒绝继续交互；不同于可恢复工具错误，不执行余下工具或额外模型请求。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMOperationRejectedException extends RuntimeException {
    /** 使用固定诊断，不携带身份、配置或原始异常。 */
    public LLMOperationRejectedException() { super("宿主已拒绝继续执行交互动作。"); }
}
