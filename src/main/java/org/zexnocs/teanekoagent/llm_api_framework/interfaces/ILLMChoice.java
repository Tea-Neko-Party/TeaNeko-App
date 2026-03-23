package org.zexnocs.teanekoagent.llm_api_framework.interfaces;

import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMAssistantMessage;

import java.util.Map;

/**
 * 模型回复消息具体类型。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 */
public interface ILLMChoice {
    /**
     * 模型停止生成 token 的原因
     * 1. "stop": 自然停止，或者遇到 "stop" 序列中列出的字符串
     * 2. "length": 输出长度达到了模型上下文的最大限制 或者 `max_tokens` 参数的限制
     * 3. "content_filter": 内容过滤器阻止了进一步的输出
     * 4. "insufficient_system_resource": 系统资源不足，无法继续生成
     *
     * @return {@link String }
     */
    String getFinishReason();

    /**
     * 该 completion 在模型生成的 completion 的选择列表中的索引。
     *
     * @return int
     */
    int getIndex();

    /**
     * 生成的 completion 消息
     *
     * @return {@link ILLMAssistantMessage }
     */
    ILLMAssistantMessage getMessage();

    /**
     * 如果请求中启用了 logprobs，则包含与生成的 tokens 相关的日志概率信息
     *
     * @return {@link Map }<{@link String }, {@link Object }>
     */
    Map<String, Object> getLogprobs();
}
