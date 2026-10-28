package org.zexnocs.teanekoagent.llm.framework.model;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.*;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大语言模型注册与调用服务。
 * <br>只扫描带有 {@link LLMModel} 注解且实现 {@link ILLMModel} 的 Bean，并通过注解声明的 {@link LLMModelId} 路由。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
@Service
public class LLMModelService extends AbstractScanner implements ILLMModelService {
    /**
     * Spring Bean 扫描器，用于发现带有 {@link LLMModel} 注解的 {@link ILLMModel} 实例。
     */
    private final IBeanScanner beanScanner;

    /**
     * LLM 文件配置服务，用于解析模型适配器默认 options。
     */
    private final ILLMFileConfigService llmFileConfigService;

    /**
     * 默认模型路由端口，不依赖 Agent 配置类型。
     */
    private final IDefaultModelResolver defaultModelResolver;

    /**
     * 模型注册表。
     * <br>键为模型适配器注册 ID，值为对应的模型适配器实例；使用并发映射以支持扫描和查询过程中的线程安全访问。
     */
    private final Map<LLMModelId, ILLMModel> modelMap = new ConcurrentHashMap<>();

    /**
     * 创建大语言模型注册与调用服务。
     *
     * @param beanScanner Spring Bean 扫描器
     * @param llmFileConfigService LLM 文件配置服务
     * @param defaultModelResolver 默认模型路由端口
     */
    public LLMModelService(IBeanScanner beanScanner,
                           ILLMFileConfigService llmFileConfigService,
                           IDefaultModelResolver defaultModelResolver) {
        this.beanScanner = beanScanner;
        this.llmFileConfigService = llmFileConfigService;
        this.defaultModelResolver = defaultModelResolver;
    }

    /**
     * 获取所有已注册的大语言模型实例。
     * <br>返回的是注册表快照，外部调用方不能直接修改内部状态。
     *
     * @return 模型适配器 ID 到模型实例的映射
     */
    @Override
    public Map<LLMModelId, ILLMModel> getModels() {
        return Map.copyOf(modelMap);
    }

    /**
     * 获取文件配置中声明的默认模型适配器 ID。
     *
     * @return 默认模型适配器 ID
     * @throws IllegalStateException 当文件配置未声明默认模型适配器 ID 时抛出
     */
    @Override
    public LLMModelId getDefaultModelId() {
        return defaultModelResolver.findDefaultModelId()
                .orElseThrow(() -> new LLMConfigurationException(
                        "Default LLM model route is not configured."));
    }

    /**
     * 获取指定模型适配器的默认 options。
     *
     * @param modelId 模型适配器 ID
     * @return 合并后的默认 options
     */
    @Override
    public LLMModelOptions getDefaultOptions(LLMModelId modelId) {
        return llmFileConfigService.getDefaultOptions(modelId, getModel(modelId).getBaseOptions());
    }

    /**
     * 根据模型适配器 ID 获取已注册的大语言模型实例。
     *
     * @param modelId 模型适配器 ID
     * @return 对应的大语言模型实例
     * @throws IllegalArgumentException 当模型未注册时抛出
     */
    @Override
    public ILLMModel getModel(LLMModelId modelId) {
        var model = modelMap.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException("LLM model is not registered: " + modelId);
        }
        return model;
    }

    /**
     * 使用指定模型适配器执行一次大语言模型调用。
     * <br>调用前会校验模型是否存在以及本次调用选项是否被该模型支持。
     *
     * @param modelId 模型适配器 ID
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     * @throws IllegalArgumentException 当模型未注册或调用选项不受支持时抛出
     */
    @Override
    public TaskFuture<ILLMResult> call(LLMModelId modelId, ILLMPrompt prompt) {
        return callInternal(modelId, prompt, null);
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param modelId 模型适配器注册标识
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Override
    public TaskFuture<ILLMResult> call(LLMModelId modelId, ILLMPrompt prompt, ExecutionPolicy execution) {
        java.util.Objects.requireNonNull(execution, "execution").checkActive();
        return callInternal(modelId, prompt, execution);
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Override
    public TaskFuture<ILLMResult> call(ILLMPrompt prompt, ExecutionPolicy execution) {
        java.util.Objects.requireNonNull(execution, "execution").checkActive();
        return callInternal(resolveModelId(prompt), prompt, execution);
    }

    /**
     * 取得本次有效参数以支持预算预检，复用单次调用校验且不执行供应商请求。
     * @param prompt 固定消息和覆盖参数
     * @return 有效参数副本，调用方可进一步收紧输出上限
     * @throws IllegalArgumentException 当路由、选项或内容不受支持时
     * @throws LLMConfigurationException 当本地模型配置不可用时
     */
    @Override public LLMModelOptions resolveOptions(ILLMPrompt prompt) {
        var id = resolveModelId(prompt);
        return validateOptions(id, getModel(id), prompt);
    }

    /**
     * 按显式策略准备参数并校验能力；已解析输入不再读取文件或适配器默认值。
     * @param modelId 可信路由
     * @param model 已注册模型适配器
     * @param prompt 本次输入
     * @return 已校验参数
     * @throws IllegalArgumentException 当模型不支持输入或选项时
     */
    private LLMModelOptions validateOptions(LLMModelId modelId, ILLMModel model, ILLMPrompt prompt) {
        LLMOptionBoundary.validate(prompt.getOptions());
        var options = prompt.getOptionsPolicy() == LLMOptionsPolicy.RESOLVED
                ? LLMOptionBoundary.requireResolved(prompt.getOptions())
                : buildEffectiveOptions(modelId, model.getBaseOptions(), prompt.getOptions());
        if (!modelId.id().equals(options.getProvider())) {
            throw new IllegalArgumentException("已解析参数的 provider 与调用路由不一致。");
        }
        LLMOptionBoundary.validate(options);
        if (!model.supports(options)) {
            throw new IllegalArgumentException("模型不支持当前输入参数：" + modelId);
        }
        LLMInputValidator.validate(prompt.getMessages(), model.getCapabilities(options.getModel()));
        return options;
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param modelId 模型适配器注册标识
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private TaskFuture<ILLMResult> callInternal(LLMModelId modelId, ILLMPrompt prompt, ExecutionPolicy execution) {
        var model = getModel(modelId);
        var options = validateOptions(modelId, model, prompt);
        var effective = new LLMPrompt(prompt.getMessages(), options, LLMOptionsPolicy.RESOLVED);
        return execution == null ? model.call(effective) : model.call(effective, execution);
    }

    /**
     * 使用默认模型适配器执行一次大语言模型调用。
     * <br>如果 prompt options 中提供 provider，则将其作为模型注册 ID；model 只作为本次调用的模型名称覆盖项。
     *
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     * @throws IllegalStateException 当未配置默认模型适配器且 prompt 未提供 provider 时抛出
     */
    @Override
    public TaskFuture<ILLMResult> call(ILLMPrompt prompt) {
        return call(resolveModelId(prompt), prompt);
    }

    /**
     * 扫描并注册 Spring 容器中带有 {@link LLMModel} 注解的大语言模型适配器。
     * <br>注册 ID 仅使用注解中的 {@link LLMModel#id()}；未标注的 {@link ILLMModel} Bean 不会注册。
     * <br>当存在重复 {@link LLMModelId} 时会立即抛出异常，避免调用时路由到不确定的模型实例。
     *
     * @throws IllegalStateException 当发现重复模型 ID 时抛出
     */
    @Override
    protected void _scan() {
        var modelPairs = beanScanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class);
        for (var pair : modelPairs.values()) {
            var annotation = pair.first();
            var model = pair.second();
            var modelId = parseModelId(annotation);
            var existing = modelMap.putIfAbsent(modelId, model);
            if (existing != null) {
                throw new IllegalStateException("Duplicate LLM model id: " + modelId);
            }
        }
    }

    /**
     * 将模型注解中的 ID 转换为模型注册 ID。
     *
     * @param annotation 模型注册注解
     * @return 模型注册 ID
     * @throws IllegalStateException 当注解 ID 为空时抛出
     */
    private static LLMModelId parseModelId(LLMModel annotation) {
        var id = annotation.id();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("LLM model annotation id must not be blank.");
        }
        return LLMModelId.of(id);
    }

    /**
     * 清空模型注册表。
     * <br>通常由重新扫描流程调用，用于在重新加载模型 Bean 前释放旧注册信息。
     */
    @Override
    protected void _clear() {
        modelMap.clear();
    }

    /**
     * 构造实际调用时使用的 options。
     * <br>合并顺序为：模型 base options、文件配置 default options、本次 prompt options。
     * <br>最终会强制把当前注解注册 ID 写回 provider option，避免 prompt options 与实际路由 ID 不一致。
     *
     * @param modelId 模型适配器 ID
     * @param baseOptions 模型代码自带的 base options
     * @param promptOptions 本次 prompt options
     * @return 实际调用 options
     */
    private LLMModelOptions buildEffectiveOptions(LLMModelId modelId,
                                                  ILLMModelOptions baseOptions,
                                                  ILLMModelOptions promptOptions) {
        var defaultOptions = llmFileConfigService.getDefaultOptions(modelId, baseOptions);
        var merged = LLMModelOptions.merge(defaultOptions, promptOptions);
        return LLMModelOptions.merge(merged, LLMModelOptions.builder()
                .provider(modelId.id())
                .build());
    }

    /**
     * 解析本次调用的目标模型适配器 ID。
     * <br>prompt options 显式提供 provider 时优先使用 prompt，否则使用文件配置中的默认模型适配器。
     *
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 目标模型适配器 ID
     */
    private LLMModelId resolveModelId(ILLMPrompt prompt) {
        if (prompt.getOptionsPolicy() == LLMOptionsPolicy.RESOLVED) {
            return LLMModelId.of(LLMOptionBoundary.requireResolved(prompt.getOptions()).getProvider());
        }
        var options = prompt.getOptions();
        if (options != null) {
            var provider = options.findProvider();
            if (provider.isPresent()) {
                return LLMModelId.of(provider.get());
            }
        }
        return getDefaultModelId();
    }
}
