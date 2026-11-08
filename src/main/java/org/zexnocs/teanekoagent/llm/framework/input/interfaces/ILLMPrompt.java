package org.zexnocs.teanekoagent.llm.framework.input.interfaces;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

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
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.9.0-l
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
