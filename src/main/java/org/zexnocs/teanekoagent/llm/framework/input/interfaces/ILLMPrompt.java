package org.zexnocs.teanekoagent.llm.framework.input.interfaces;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

import java.util.List;

/**
 * LLM 输入的接口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
public interface ILLMPrompt {
    /**
     * 获取 LLM 输入的消息。
     *
     * @return 保持输入顺序的统一消息列表
     */
    @NonNull
    List<ILLMMessage> getMessages();

    /**
     * 获取 LLM Options。
     *
     * @return 本次参数；null 表示未提供覆盖项
     */
    @Nullable
    ILLMModelOptions getOptions();

    /**
     * 指定本地参数是否仍需合并默认值；已解析参数必须显式包含 provider 和 model。
     * 策略不参与供应商 JSON，也不授予执行权限；自定义模型适配器须遵守此约定。
     *
     * @return 本地参数解析策略，旧调用默认继续合并
     */
    @JsonIgnore
    default LLMOptionsPolicy getOptionsPolicy() { return LLMOptionsPolicy.MERGE_DEFAULTS; }
}
