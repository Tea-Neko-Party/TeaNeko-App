package org.zexnocs.teanekoagent.llm.framework.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

import java.util.List;
import java.util.Objects;

/**
 * 统一模型输入容器。调用方在交付边界冻结数据，复制期间不得并发修改原消息或选项。
 * 已解析策略只固定默认值合并规则，不替代参数快照、能力验证或执行控制。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
public class LLMPrompt implements ILLMPrompt {
    /// LLM 输入的消息
    @Getter
    private final List<ILLMMessage> messages;

    /// LLM 输入选项
    @Getter
    @Nullable
    private final ILLMModelOptions options;

    private final LLMOptionsPolicy optionsPolicy;

    /**
     * 创建继续合并默认值的输入，保留既有调用方式。
     * @param messages 有序统一消息
     * @param options 可选的本次参数覆盖
     */
    public LLMPrompt(List<ILLMMessage> messages, @Nullable ILLMModelOptions options) {
        this(messages, options, LLMOptionsPolicy.MERGE_DEFAULTS);
    }

    /**
     * 显式选择参数解析策略；RESOLVED 输入须由宿主先解析并冻结，不能以此跳过能力校验。
     * @param messages 有序统一消息
     * @param options 本次选项；已解析策略要求完整的 provider 和 model
     * @param optionsPolicy 本地解析策略，不进入供应商参数
     * @throws NullPointerException 当策略为空时
     */
    public LLMPrompt(List<ILLMMessage> messages, @Nullable ILLMModelOptions options, LLMOptionsPolicy optionsPolicy) {
        this.messages = messages;
        this.options = options;
        this.optionsPolicy = Objects.requireNonNull(optionsPolicy, "模型参数解析策略不能为空。");
    }

    /**
     * 返回只在本地使用的解析策略，禁止将其序列化为供应商扩展字段。
     * @return 本次参数解析策略
     */
    @Override @JsonIgnore
    public LLMOptionsPolicy getOptionsPolicy() { return optionsPolicy; }

    /**
     * 使用默认选项与指定消息列表构造输入。
     *
     * @param messages 有序统一消息
     */
    public LLMPrompt(List<ILLMMessage> messages) {
        this(messages, null);
    }

    /**
     * 使用默认选项与单条消息构造输入。
     *
     * @param message 单个统一消息
     */
    public LLMPrompt(ILLMMessage message) {
        this(List.of(message), null);
    }

    /**
     * 使用指定选项与单条消息构造输入。
     *
     * @param message 单个统一消息
     * @param options 输入选项
     */
    public LLMPrompt(ILLMMessage message, @Nullable ILLMModelOptions options) {
        this(List.of(message), options);
    }
}
