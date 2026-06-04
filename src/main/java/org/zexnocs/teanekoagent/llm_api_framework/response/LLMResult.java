package org.zexnocs.teanekoagent.llm_api_framework.response;

import lombok.*;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm_api_framework.response.interfaces.ILLMChoice;
import org.zexnocs.teanekoagent.llm_api_framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm_api_framework.response.interfaces.ILLMUsage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 大语言模型调用结果。
 * <br>用于统一承载不同供应商返回的 completion/chat response。
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
public class LLMResult implements ILLMResult {
    /**
     * 本次模型响应的唯一标识。
     */
    private String id;

    /**
     * 模型生成的候选响应列表。
     * <br>多数场景只返回一个候选项，但框架保留多候选项结构以兼容不同供应商。
     */
    @Builder.Default
    private List<ILLMChoice> choices = List.of();

    /**
     * 响应对象类型。
     * <br>默认使用 OpenAI Chat Completions 风格的 {@code chat.completion}。
     */
    @Builder.Default
    private String object = "chat.completion";

    /**
     * 响应创建时间戳。
     * <br>单位为秒，默认使用当前时间。
     */
    @Builder.Default
    private long created = Instant.now().getEpochSecond();

    /**
     * 生成本次响应所使用的模型名称。
     */
    private String model;

    /**
     * 本次请求和响应的 token 用量信息。
     */
    @Builder.Default
    private ILLMUsage usage = LLMUsage.empty();

    /**
     * 获取第一个候选响应。
     * <br>当候选列表为空或未设置时返回 {@link Optional#empty()}。
     *
     * @return 第一个候选响应
     */
    public Optional<ILLMChoice> getFirstChoice() {
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(choices.getFirst());
    }

    /**
     * 获取第一个候选响应中的助手消息。
     * <br>当候选列表为空、首个候选项为空或消息为空时返回 {@link Optional#empty()}。
     *
     * @return 第一个助手消息
     */
    public Optional<ILLMAssistantMessage> getFirstMessage() {
        return getFirstChoice().map(ILLMChoice::getMessage);
    }
}
