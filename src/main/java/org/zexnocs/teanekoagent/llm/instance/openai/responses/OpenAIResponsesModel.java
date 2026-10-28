package org.zexnocs.teanekoagent.llm.instance.openai.responses;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.instance.openai.OpenAIVisionCapabilities;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.List;

/**
 * OpenAI Responses API 模型适配器。
 * <br>注册 ID 为 {@code openai}，默认使用 {@code gpt-5.5} 和非流式 {@code /responses} 调用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.1
 * @version 4.5.0-l
 */
@LLMModel(id = OpenAIResponsesModel.PROVIDER)
public class OpenAIResponsesModel extends AbstractLLMModel {
    /** OpenAI 模型适配器注册 ID。 */
    public static final String PROVIDER = "openai";

    /** OpenAI 默认模型名称。 */
    public static final String DEFAULT_MODEL = OpenAIModelOptions.DEFAULT_MODEL;

    /** API 响应服务。 */
    private final IAPIResponseService apiResponseService;

    private final ICredentialResolver credentialResolver;

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
     * 创建 OpenAI Responses 模型适配器。
     *
     * @param apiResponseService API 响应服务
     * @param credentialResolver Spring 凭据与可信连接解析端口
     */
    public OpenAIResponsesModel(IAPIResponseService apiResponseService, ICredentialResolver credentialResolver) {
        super(PROVIDER, DEFAULT_MODEL, OpenAIModelOptions.baseOptions());
        this.apiResponseService = apiResponseService;
        this.credentialResolver = credentialResolver;
    }

    /**
     * 判断 OpenAI Responses 适配器是否支持给定参数。
     * <br>当前实现仅处理非流式响应；Responses API 未声明的 stop、frequency penalty 和 presence penalty 会被拒绝。
     *
     * @param options 模型参数
     * @return 参数能够完整映射时返回 {@code true}
     */
    @Override
    public boolean supports(ILLMModelOptions options) {
        var standardOptions = LLMModelOptions.copyOf(options);
        var providerSupported = standardOptions.findProvider()
                .map(PROVIDER::equals)
                .orElse(true);
        var responseFormatSupported = standardOptions.findResponseFormat()
                .map(format -> format == LLMResponseFormat.TEXT || format == LLMResponseFormat.JSON)
                .orElse(true);
        var streamSupported = standardOptions.findStream()
                .map(stream -> !stream)
                .orElse(true);
        var stopSupported = standardOptions.findStopWords()
                .map(List::isEmpty)
                .orElse(true);
        return providerSupported
                && responseFormatSupported
                && streamSupported
                && stopSupported
                && standardOptions.findFrequencyPenalty().isEmpty()
                && standardOptions.findPresencePenalty().isEmpty();
    }

    /**
     * 调用 OpenAI Responses API。
     *
     * @param prompt 本次调用的 Prompt
     * @param options 已完成合并的模型参数
     * @return OpenAI 响应转换后的统一 LLM Result Future
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
        return doCall(prompt, options, null);
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param options 本次有效模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options, ExecutionPolicy execution) {
        if (execution != null) {
            execution.checkActive();
        }
        var openAIOptions = OpenAIModelOptions.copyOf(options);
        var access = credentialResolver.require(PROVIDER);
        var request = OpenAIResponsesMapper.toRequest(prompt, openAIOptions, access.apiKey());
        request.setBaseUrl(access.baseUrlOr(OpenAIModelOptions.DEFAULT_BASE_URL));
        request.setApiPath(access.apiPathOr(OpenAIModelOptions.DEFAULT_API_PATH));
        try {
            if (execution != null) {
                execution.checkActive();
            }
            var response = execution == null
                    ? apiResponseService.addTask(request, OpenAIResponsesResponseData.class, true, true)
                    : apiResponseService.addTask(request, OpenAIResponsesResponseData.class, true, true, execution);
            return response
                    .thenApply(OpenAIResponsesMapper::toResult);
        } catch (ExecutionStoppedException stopped) {
            throw stopped;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI Responses API request failed.", exception);
        }
    }

}
