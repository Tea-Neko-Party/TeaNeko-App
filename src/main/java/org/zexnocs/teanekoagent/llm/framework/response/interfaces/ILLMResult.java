package org.zexnocs.teanekoagent.llm.framework.response.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * LLM 获取的结果接口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
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
     * @return 按供应商顺序排列的响应候选
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
     * 返回供应商报告的请求用量；缺失时不得按零用量或免费调用处理。
     *
     * @return 供应商 token 数据，未提供时为 null
     */
    @Nullable
    ILLMUsage getUsage();

    /**
     * 获取第一个生成的选项。
     *
     * @return 第一个响应候选，列表为空时无值
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
     * @return 第一个候选的模型消息，候选或消息缺失时无值
     */
    default Optional<ILLMAssistantMessage> getFirstMessage() {
        return getFirstChoice().map(ILLMChoice::getMessage);
    }
}
