package org.zexnocs.teanekoagent_old.llm.instance.kimi;

import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent_old.llm.instance.openai.completions.AbstractOpenAIChatCompletionModel;
import org.zexnocs.teanekoagent_old.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

/**
 * Kimi Chat Completions 模型适配器。
 * <br>注册 ID 为 {@code kimi}，通过 OpenAI Chat Completions 兼容基类复用消息、工具和响应映射。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@LLMModel(id = KimiChatModel.PROVIDER)
public class KimiChatModel extends AbstractOpenAIChatCompletionModel {
    /** Kimi 模型适配器注册 ID。 */
    public static final String PROVIDER = "kimi";

    /** Kimi 默认模型名称。 */
    public static final String DEFAULT_MODEL = KimiModelOptions.DEFAULT_MODEL;

    /**
     * 创建 Kimi 模型适配器。
     *
     * @param apiResponseService API 响应服务
     */
    public KimiChatModel(IAPIResponseService apiResponseService) {
        super(PROVIDER, DEFAULT_MODEL, KimiModelOptions.baseOptions(), apiResponseService);
    }

    /**
     * 将统一参数转换为 Kimi 参数。
     *
     * @param options 已合并的统一参数
     * @return Kimi 参数
     */
    @Override
    protected OpenAIChatCompletionModelOptions prepareOptions(LLMModelOptions options) {
        return KimiModelOptions.copyOf(options);
    }

    /**
     * 固定使用 Kimi 官方 API 地址。
     *
     * @param options Chat Completions 参数
     * @return Kimi 官方 base URL
     */
    @Override
    protected String resolveBaseUrl(OpenAIChatCompletionModelOptions options) {
        return KimiModelOptions.DEFAULT_BASE_URL;
    }

    /**
     * 校验 Kimi 模型的参数限制。
     *
     * @param options 已转换的 Chat Completions 参数
     * @return Kimi 支持时返回 {@code true}
     */
    @Override
    protected boolean supportsChatCompletionOptions(OpenAIChatCompletionModelOptions options) {
        var model = options.findModel().orElse(DEFAULT_MODEL);
        if (options.findLogprobs().orElse(false)) {
            return false;
        }
        if (!isK2Model(model)) {
            return true;
        }
        var unsupportedSampling = options.findTemperature().isPresent()
                || options.findTopP().isPresent()
                || options.findFrequencyPenalty().isPresent()
                || options.findPresencePenalty().isPresent()
                || options.findStopWords().filter(stopWords -> !stopWords.isEmpty()).isPresent();
        if (unsupportedSampling) {
            return false;
        }
        return !model.startsWith(KimiModelOptions.KIMI_K2_7_CODE)
                || options.findThinking().orElse(true);
    }

    /**
     * 判断模型是否属于 Kimi K2 系列。
     *
     * @param model 模型名称
     * @return 属于 K2 系列时返回 {@code true}
     */
    private static boolean isK2Model(String model) {
        return model != null && model.startsWith("kimi-k2");
    }
}
