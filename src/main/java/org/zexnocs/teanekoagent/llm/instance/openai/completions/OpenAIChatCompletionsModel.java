package org.zexnocs.teanekoagent.llm.instance.openai.completions;

import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.LinkedHashMap;

/**
 * OpenAI Chat Completions 兼容模型实现。
 * <br>该类未使用 {@code LLMModel} 注解，不会注册到模型服务；用于复用 OpenAI Chat Completions 专用参数逻辑和测试兼容层。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public class OpenAIChatCompletionsModel extends AbstractOpenAIChatCompletionModel {
    /** OpenAI Chat Completions 调用使用的 provider。 */
    public static final String PROVIDER = "openai-completions";

    /** 默认模型名称。 */
    public static final String DEFAULT_MODEL = OpenAIChatCompletionModelOptions.DEFAULT_MODEL;

    /**
     * 创建 OpenAI Chat Completions 模型适配器。
     *
     * @param apiResponseService API 响应服务
     */
    public OpenAIChatCompletionsModel(IAPIResponseService apiResponseService) {
        super(PROVIDER, DEFAULT_MODEL, OpenAIChatCompletionModelOptions.baseOptions(), apiResponseService);
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
