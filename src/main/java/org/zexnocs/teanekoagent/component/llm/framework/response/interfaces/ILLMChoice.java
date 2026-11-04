package org.zexnocs.teanekoagent.component.llm.framework.response.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.Map;

/**
 * 模型回复消息具体类型。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：修正概率信息返回说明中的无效 HTML。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 * @version 4.8.0-l
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
     * 如果请求中启用了 logprobs，则包含与生成的 tokens 相关的日志概率信息。
     * 2026/11/05：以中文说明键值语义，不直接输出泛型 HTML。
     *
     * @return 以字段名为键的模型日志概率信息
     */
    Map<String, Object> getLogprobs();
}
