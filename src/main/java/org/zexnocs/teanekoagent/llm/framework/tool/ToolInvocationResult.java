package org.zexnocs.teanekoagent.llm.framework.tool;

import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

/**
 * 不包含原异常、可信绑定或执行器的回填结果；截断与业务完成分别表达。
 *
 * @param call 模型产生的工具调用，身份不从参数中取得
 * @param status 本次处理的终止状态
 * @param output 用于本次处理的 {@code output} 数据
 * @param truncated 用于本次处理的 {@code truncated} 数据
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record ToolInvocationResult(ILLMToolCall call, Status status, String output, boolean truncated) {
    /**
     * 工具执行与拒绝的结构化状态，不包含底层敏感异常。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status { SUCCESS, INVALID_CALL, NOT_ALLOWED, INVALID_ARGUMENTS, FAILED, CANCELLED, TIMED_OUT }

    /**
     * 生成保留原调用 ID 的工具回填消息；非法调用不得进入正常历史，避免破坏协议配对。
     *
     * @return 与原 tool call ID 配对的工具消息
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     */
    public LLMToolMessage toMessage() {
        if (status == Status.INVALID_CALL) {
            throw new IllegalStateException("An invalid tool call cannot be added to model history.");
        }
        return LLMToolMessage.builder()
                .name(call.getName())
                .toolCallId(call.getId())
                .contents(new LLMContentListBuilder().addText(output).build())
                .build();
    }
}
