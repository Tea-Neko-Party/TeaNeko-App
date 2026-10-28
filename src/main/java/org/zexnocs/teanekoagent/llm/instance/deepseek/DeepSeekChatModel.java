package org.zexnocs.teanekoagent.llm.instance.deepseek;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

/**
 * DeepSeek 对话补全模型适配器。
 * <br>注册 ID 为 {@code deepseek}，默认模型名称为 {@code deepseek-v4-flash}。
 * <br>API key 及可选连接覆盖由凭据端口在请求边界解析，不进入 Options。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
@LLMModel(id = DeepSeekChatModel.PROVIDER)
public class DeepSeekChatModel extends AbstractLLMModel {
    /**
     * DeepSeek 模型适配器注册 ID。
     */
    public static final String PROVIDER = "deepseek";

    /**
     * 独立适配器调用的内建模型；模型服务要求本地文件显式选择 model。
     */
    public static final String DEFAULT_MODEL = DeepSeekModelOptions.DEFAULT_MODEL;

    /**
     * DeepSeek 对话补全接口路径。
     */
    public static final String DEFAULT_API = DeepSeekModelOptions.DEFAULT_API_PATH;

    /**
     * base URL 在 metadata 中的字段名。
     */
    public static final String BASE_URL_METADATA = DeepSeekModelOptions.BASE_URL_METADATA;

    /**
     * API path 在 metadata 中的字段名。
     */
    public static final String API_METADATA = DeepSeekModelOptions.API_PATH_METADATA;

    /**
     * API 响应服务。
     * <br>DeepSeek 的 HTTP 请求统一经由 TeaNeko Core 的 api_response 模块发送。
     */
    private final IAPIResponseService apiResponseService;

    private final ICredentialResolver credentialResolver;

    /**
     * 创建 DeepSeek 对话补全模型适配器。
     *
     * @param apiResponseService API 响应服务
     * @param credentialResolver Spring 凭据与可信连接解析端口
     */
    public DeepSeekChatModel(IAPIResponseService apiResponseService, ICredentialResolver credentialResolver) {
        super(PROVIDER, DEFAULT_MODEL, DeepSeekModelOptions.baseOptions());
        this.apiResponseService = apiResponseService;
        this.credentialResolver = credentialResolver;
    }

    /**
     * 判断 DeepSeek 适配器是否支持给定 options。
     * <br>当前实现支持非流式对话补全、文本输出和 JSON Object 输出。
     *
     * @param options 模型调用参数
     * @return 如果支持该调用参数则返回 {@code true}
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
        return providerSupported && responseFormatSupported && streamSupported;
    }

    /**
     * 按本次具体模型名返回已验证的能力，不将供应商默认模型能力套用到未知名称。
     *
     * @param model 本次实际选中的具体模型名称
     * @return 按实际模型或显式能力集合构造的不可变能力声明
     */
    @Override
    public ModelCapabilities getCapabilities(String model) {
        return model != null && java.util.Set.of("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp").contains(model)
                ? ModelCapabilities.userImages(java.util.Set.of(LLMImageDetail.values()))
                : ModelCapabilities.textOnly();
    }

    /**
     * 调用 DeepSeek 对话补全 API。
     *
     * @param prompt 本次调用的提示词与消息
     * @param options 已完成合并的模型调用选项
     * @return DeepSeek 响应转换后的统一 LLM 结果 Future
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
        var deepSeekOptions = DeepSeekModelOptions.copyOf(options);
        var access = credentialResolver.require(PROVIDER);
        var request = DeepSeekChatCompletionMapper.toRequest(prompt,
                deepSeekOptions,
                access.baseUrlOr(DeepSeekModelOptions.DEFAULT_BASE_URL),
                access.apiPathOr(DEFAULT_API),
                access.apiKey());
        try {
            if (execution != null) {
                execution.checkActive();
            }
            var response = execution == null
                    ? apiResponseService.addTask(request, DeepSeekChatCompletionResponseData.class, true, true)
                    : apiResponseService.addTask(request, DeepSeekChatCompletionResponseData.class, true, true, execution);
            return response
                    .thenApply(DeepSeekChatCompletionMapper::toResult);
        } catch (ExecutionStoppedException stopped) {
            throw stopped;
        } catch (Exception exception) {
            throw new IllegalStateException("DeepSeek chat completion request failed.", exception);
        }
    }

}
