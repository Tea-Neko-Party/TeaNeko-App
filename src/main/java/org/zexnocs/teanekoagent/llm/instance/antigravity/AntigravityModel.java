package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonSupport;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * 使用 Gemini 原生协议访问模型代理，独立于 Agent 业务及代理账号管理。
 * <br>2026/11/10：复用现有 HTTP、凭据代次、执行控制及结构化生成契约。
 * <br>2026/11/10：默认使用 SSE 传输并聚合完整响应，显式 stream=false 保留非流式方式。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@LLMModel(id = AntigravityModel.PROVIDER)
public final class AntigravityModel extends AbstractLLMModel {
    public static final String PROVIDER = "antigravity";
    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:8045";
    public static final String DEFAULT_API_PATH = "/v1beta/models";
    private final IAPIResponseService api;
    private final ICredentialResolver credentials;
    private final ObjectMapper mapper;

    /**
     * 注入宿主资源，不创建独立网络客户端或调度器。
     * @param api 原 HTTP 服务
     * @param credentials 可信凭据解析器
     * @param mapper 应用 JSON 转换器
     */
    public AntigravityModel(IAPIResponseService api, ICredentialResolver credentials, ObjectMapper mapper) {
        super(PROVIDER, DEFAULT_MODEL, LLMModelOptions.builder().provider(PROVIDER).model(DEFAULT_MODEL).build());
        this.api = api;
        this.credentials = credentials;
        this.mapper = mapper;
    }

    /**
     * @param model 实际型号
     * @return 原生 Schema 声明，远端仍须由本地严格校验复核。
     */
    @Override
    public LLMJsonSupport getJsonOutputSupport(String model) {
        return LLMJsonSupport.SCHEMA;
    }

    /**
     * @param model 实际型号
     * @return 仅内建样板型号默认允许视觉，代理别名由 image-input 显式声明。
     */
    @Override
    public ModelCapabilities getCapabilities(String model) {
        return DEFAULT_MODEL.equals(model) ? vision() : ModelCapabilities.textOnly();
    }

    /**
     * @param options 已解析参数
     * @return 图片声明优先于默认型号能力。
     */
    @Override
    public ModelCapabilities inputCapabilities(ILLMModelOptions options) {
        return options.findImageInput().map(enabled -> enabled ? vision() : ModelCapabilities.textOnly())
                .orElseGet(() -> getCapabilities(options.getModel()));
    }

    /** @return 支持原生用户图片及三种既有细节声明。 */
    private static ModelCapabilities vision() {
        return ModelCapabilities.userImages(Set.of(LLMImageDetail.AUTO, LLMImageDetail.LOW, LLMImageDetail.HIGH));
    }

    /**
     * @param options 待调用参数
     * @return 是否为本协议路由且可以完整映射，不静默丢弃对数概率请求。
     */
    @Override
    public boolean supports(ILLMModelOptions options) {
        return options.findProvider().map(route -> PROVIDER.equals(route)
                || credentials.supportsRoute(route, PROVIDER)).orElse(true)
                && !options.findLogprobs().orElse(false)
                && options.findStopWords().map(words -> words.size() <= 5).orElse(true);
    }

    /**
     * @param prompt 完整消息
     * @param options 已解析参数
     * @return 单次模型任务，无隐含重试。
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
        return doCall(prompt, options, null);
    }

    /**
     * @param prompt 完整消息
     * @param options 同代次参数
     * @param execution 可选阶段控制，取消传递至实际 HTTP 任务
     * @return 保留真实完成信号的映射结果
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options,
            @Nullable ExecutionPolicy execution) {
        if (execution != null) {
            execution.checkActive();
        }
        var access = credentials.require(PROVIDER, options);
        var request = AntigravityRequestMapper.toRequest(prompt, options, access, mapper);
        if (execution != null) {
            execution.checkActive();
        }
        // 2026/11/10：流式只改变传输，工具和用户回复继续只接收经完整性验证的终态。
        var response = options.findStream().orElse(true)
                ? api.addStreamTask(request, AntigravityResponseData.class,
                        () -> new AntigravityStreamAccumulator(mapper),
                        execution == null ? ExecutionPolicy.defaults() : execution)
                : execution == null ? api.addTask(request, AntigravityResponseData.class, true, true)
                        : api.addTask(request, AntigravityResponseData.class, true, true, execution);
        return response.thenApply(value -> AntigravityResponseMapper.toResult(value, mapper));
    }
}
