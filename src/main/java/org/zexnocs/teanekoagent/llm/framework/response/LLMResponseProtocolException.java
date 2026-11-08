package org.zexnocs.teanekoagent.llm.framework.response;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 模型响应违反工具协议时的安全诊断，不保存供应商正文或工具参数。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
public final class LLMResponseProtocolException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建正文混入内部工具调用标记的固定错误，不接收模型原文。 */
    public LLMResponseProtocolException() {
        super("模型将内部工具调用标记放入正文，没有返回结构化 tool_calls；本轮已停止发送，请检查模型或协议适配器。");
    }

    /** @return 可公开且不含模型原文的协议错误原因 */
    @Override
    public String failureReason() {
        return getMessage();
    }
}
