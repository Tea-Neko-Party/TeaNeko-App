package org.zexnocs.teanekoagent_old.llm.framework.response;

import lombok.*;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMChoice;

import java.util.Map;

/**
 * 大语言模型响应候选项。
 * <br>表示一次模型响应中的一个 choice，包含生成消息、结束原因和可选 logprobs。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMChoice implements ILLMChoice {
    /**
     * 模型停止生成 token 的原因。
     * <br>常见取值包括 {@code stop}、{@code length}、{@code content_filter} 等。
     */
    private String finishReason;

    /**
     * 当前候选项在模型响应候选列表中的索引。
     */
    private int index;

    /**
     * 模型生成的助手消息。
     * <br>该消息可能包含文本内容，也可能包含模型发起的工具调用。
     */
    private ILLMAssistantMessage message;

    /**
     * 生成 token 的日志概率信息。
     * <br>仅当调用选项开启 logprobs 并且供应商返回该字段时包含实际内容。
     */
    @Builder.Default
    private Map<String, Object> logprobs = Map.of();
}
