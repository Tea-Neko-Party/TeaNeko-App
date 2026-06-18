package org.zexnocs.teanekoagent_old.llm.framework.response.interfaces;

import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * LLM 获取的结果接口。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 */
public interface ILLMResult {
    /**
     * 获取对话的唯一标识符
     *
     * @return 对话的唯一标识符
     */
    String getId();

    /**
     * 模型生成的 completion
     *
     * @return {@link List }<{@link ILLMChoice }>
     */
    List<ILLMChoice> getChoices();

    /**
     * 对象类型。
     * <br>一般为 "chat.completion"
     *
     * @return {@link String }
     */
    String getObject();

    /**
     * 响应创建时间点
     *
     * @return 响应创建时间点
     */
    Instant getCreated();

    /**
     * 生成该 completion 所使用的模型
     *
     * @return {@link String }
     */
    String getModel();

    /**
     * 请求的用量信息
     *
     * @return {@link ILLMUsage }
     */
    ILLMUsage getUsage();

    /**
     * 获取第一个生成的选项。
     *
     * @return {@link Optional }<{@link ILLMChoice }>
     */
    default Optional<ILLMChoice> getFirstChoice() {
        var choices = getChoices();
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(choices.getFirst());
    }

    /**
     * 获取第一个生成选项的信息。
     *
     * @return {@link Optional }<{@link ILLMAssistantMessage }>
     */
    default Optional<ILLMAssistantMessage> getFirstMessage() {
        return getFirstChoice().map(ILLMChoice::getMessage);
    }
}
