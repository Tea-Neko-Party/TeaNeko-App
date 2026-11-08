package org.zexnocs.teanekoagent.llm.instance.openai.completions;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonSupport;
import org.zexnocs.teanekoagent.llm.instance.openai.OpenAIVisionCapabilities;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.LinkedHashMap;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * OpenAI Chat Completions 兼容模型实现。
 * <br>该类未使用 {@code LLMModel} 注解，不会注册到模型服务；用于复用 OpenAI Chat Completions 专用参数逻辑和测试兼容层。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：按模型声明 JSON 输出能力，旧快照不误用严格 Schema。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.1
 * @version 4.9.0-l
 */
public class OpenAIChatCompletionsModel extends AbstractOpenAIChatCompletionModel {
    /**
     * 2026/11/06：声明当前标准接口的 Schema 输出；旧模型可沿用不带 Schema 的 JSON 选项。
     * @param model 实际模型名称
     * @return 当前模型可用的原生输出约束
     */
    @Override
    public LLMJsonSupport getJsonOutputSupport(String model) {
        // 2026/11/06：旧 4o 快照仅支持对象模式，不能按共同前缀误发严格 Schema。
        if (model.equals("gpt-4o-2024-05-13")) {
            return LLMJsonSupport.OBJECT;
        }
        if (model.startsWith("gpt-4o") || model.startsWith("gpt-4.1") || model.startsWith("gpt-5")
                || model.startsWith("gpt-6") || model.startsWith("o3") || model.startsWith("o4")) {
            return LLMJsonSupport.SCHEMA;
        }
        if (model.startsWith("gpt-3.5-turbo") || model.startsWith("gpt-4-turbo")
                || model.equals("gpt-4-1106-preview") || model.equals("gpt-4-0125-preview")) {
            return LLMJsonSupport.OBJECT;
        }
        return LLMJsonSupport.NONE;
    }

    /** OpenAI Chat Completions 调用使用的 provider。 */
    public static final String PROVIDER = "openai-completions";

    /** 默认模型名称。 */
    public static final String DEFAULT_MODEL = OpenAIChatCompletionModelOptions.DEFAULT_MODEL;

    /**
     * 按本次具体模型名返回已验证的能力，不将供应商默认模型能力套用到未知名称。
     *
     * @param model 本次实际选中的具体模型名称
     * @return 按实际模型或显式能力集合构造的不可变能力声明
     */
    @Override
    public ModelCapabilities getCapabilities(String model) {
        return OpenAIVisionCapabilities.forModel(model);
    }

    /**
     * 创建 OpenAI Chat Completions 模型适配器。
     *
     * @param apiResponseService API 响应服务
     * @param credentialResolver 由装配方提供的 {@code ICredentialResolver} 协作依赖
     */
    public OpenAIChatCompletionsModel(IAPIResponseService apiResponseService, ICredentialResolver credentialResolver) {
        super(PROVIDER, DEFAULT_MODEL, OpenAIChatCompletionModelOptions.baseOptions(), apiResponseService, credentialResolver);
    }

    /**
     * 将统一 thinking 开关转换为 OpenAI Chat Completions 的 reasoning_effort。
     * <br>调用方通过 {@code body.reasoning_effort} 显式配置时保留显式值。
     *
     * @param options 已合并的统一参数
     * @return OpenAI Chat Completions 参数
     */
    @Override
    protected OpenAIChatCompletionModelOptions prepareOptions(LLMModelOptions options) {
        var result = OpenAIChatCompletionModelOptions.copyOf(options);
        var metadata = new LinkedHashMap<>(result.getMetadata());
        var reasoningEffortKey = OpenAIChatCompletionModelOptions.EXTRA_BODY_PREFIX + "reasoning_effort";
        if (!metadata.containsKey(reasoningEffortKey)) {
            result.findThinking().ifPresent(thinking -> metadata.put(
                    reasoningEffortKey,
                    thinking ? "medium" : "none"
            ));
        }
        result.setMetadata(metadata);
        return result;
    }
}
