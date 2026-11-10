package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMTokenEstimator;
import org.zexnocs.teanekoagent.llm.framework.interaction.JsonLLMTokenEstimator;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;

/**
 * 大语言模型注册与调用服务。
 * <br>只扫描带有 {@link LLMModel} 注解且实现 {@link ILLMModel} 的 Bean，并通过注解声明的 {@link LLMModelId} 路由。
 * <br>2026/11/02：模型插件调用接入归属及异步结果版本守卫。
 *
 * 2026/11/03：同步构造模型请求时固定文件代次，拒绝旧参数使用新凭据。
 * <br>2026/11/04：所有调用路径在请求前按模型上下文容量及完整输出额度执行准入。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：将未直接注册的配置路由解析为扫描注册的协议 Bean。
 * <br>2026/11/04：模型解析与请求构造使用独立配置来源，私有路由不依赖模型文件。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：模型目录完整校验后原子发布，重扫失败保留原快照。
 * <br>2026/11/06：按适配器 JSON 能力发送请求，路由覆盖不重置输出格式。
 * <br>2026/11/06：按原生能力去除重复结构提示，保持输入预检、观察与实际发送一致。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：在模型目录发布前编译归属，请求时只读取已注册方法。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：模型预检使用包含图片声明的有效参数。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.9.1-l
 */
@Service
public class LLMModelService extends AbstractScanner implements ILLMModelService {
    private org.zexnocs.teanekoagent.llm.framework.route.@org.jspecify.annotations.Nullable LLMRouteDirectory routes;

    /**
     * 2026/11/04：按来源固定配置，不再要求私有模型依赖系统模型文件。
     * @param routes 原 scanner 注册的配置来源
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installRoutes(org.zexnocs.teanekoagent.llm.framework.route.LLMRouteDirectory routes) {
        this.routes = routes;
    }

    /**
     * @param id 当前路由
     * @param action 同步参数或请求构造
     * @param <T> 结果类型
     * @return 对应来源的固定版本结果
     */
    private <T> T fixed(LLMModelId id, java.util.function.Supplier<T> action) {
        return routes == null ? fixed(action) : routes.source(id.id()).fixed(id.id(), action);
    }
    // 2026/11/04：默认使用统一保守估算；应用安装自定义估算器时与交互服务共用口径。
    private ILLMTokenEstimator contextEstimator = new JsonLLMTokenEstimator(new tools.jackson.databind.ObjectMapper());

    /**
     * 2026/11/04：复用应用的输入估算器，直接调用与交互调用采用相同容量口径。
     * @param estimator 已装配的输入估算器
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installContextEstimator(ILLMTokenEstimator estimator) {
        contextEstimator = estimator;
    }

    private org.zexnocs.teanekoagent.llm.file_config.@org.jspecify.annotations.Nullable LLMFileConfigurationEpoch fileEpoch;

    /**
     * 2026/11/03：参数预检与实际请求构造绑定原文件代次，独立宿主可不安装。
     * @param epoch 文件快照边界
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installFileEpoch(org.zexnocs.teanekoagent.llm.file_config.LLMFileConfigurationEpoch epoch) {
        fileEpoch = epoch;
    }

    /**
     * @param action 同步构造，不等待网络
     * @param <T> 返回类型
     * @return 同一文件代次内的结果
     */
    private <T> T fixed(java.util.function.Supplier<T> action) {
        return fileEpoch == null ? action.get() : fileEpoch.fixed(action);
    }
    // 2026/11/02：保持直接构造使用方式，Spring 装配时启用归属守卫。
    private org.zexnocs.teanekocore.plugin.PluginAccess pluginAccess;
    private static final java.lang.reflect.Method MODEL_CALL = modelCall();

    /** @return 2026/11/07：类初始化一次固定模型接口方法，调用时不反射发现。 */
    private static java.lang.reflect.Method modelCall() {
        try {
            return ILLMModel.class.getMethod("call", ILLMPrompt.class);
        } catch (NoSuchMethodException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    /**
     * 安装模型所属插件检查。
     * @param access 插件归属入口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installPluginAccess(org.zexnocs.teanekocore.plugin.PluginAccess access) { this.pluginAccess = access; }

    /**
     * 捕获模型 Bean 的插件版本，未声明归属的旧适配器保持原语义。
     * @param model 模型适配器
     * @return 可选归属版本
     */
    private org.zexnocs.teanekocore.plugin.PluginRegistry.Token pluginToken(ILLMModel model) {
        if (pluginAccess == null) { return null; }
        return pluginAccess.capture(model, MODEL_CALL);
    }
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
    // 2026/11/05：查询只读取完整快照，显式重扫失败保留上一目录。
    private volatile Map<LLMModelId, ILLMModel> modelMap = Map.of();

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
        // 2026/11/04：私有及系统参数均由所属配置来源合并。
        return defaults(modelId, getModel(modelId).getBaseOptions());
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
        var snapshot = modelMap;
        var model = snapshot.get(modelId);
        // 2026/11/04：别名只决定配置选择，实际执行仍使用扫描注册的协议 Bean 和插件守卫。
        if (model == null) {
            var adapter = routes != null ? routes.source(modelId.id()).adapter(modelId.id())
                    : llmFileConfigService.getConfig().findModelConfig(modelId)
                    .orElseThrow(() -> new IllegalArgumentException("所选模型没有注册或配置路由。"))
                    .adapterId();
            model = snapshot.get(adapter);
        }
        if (model == null) {
            throw new IllegalArgumentException("LLM model is not registered: " + modelId);
        }
        // 2026/11/02：被禁用的适配器不能继续参与默认参数和调用路由。
        pluginToken(model); return model;
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
        // 2026/11/03：默认路由也与参数和凭据使用同一构造边界。
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
        // 2026/11/03：旧已解析参数先检查代次，不能通过重新标记掩盖变化。
        var id = resolveModelId(prompt);
        return fixed(id, () -> {
            var options = validateOptions(id, getModel(id), prompt);
            if (routes != null) {
                routes.source(id.id()).verify(options);
                routes.source(id.id()).stamp(options);
            } else if (fileEpoch != null) {
                fileEpoch.verify(options);
                fileEpoch.stamp(options);
            }
            return options;
        });
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
        // 2026/11/06：预检按适配后的原生能力检查，保留宿主 JSON 意图供实际调用告警。
        var checked = options.findResponseFormat().orElse(LLMResponseFormat.TEXT) == LLMResponseFormat.JSON
                ? model.getJsonOutputSupport(options.getModel()).adapt(options, false) : options;
        if (!model.supports(checked)) {
            throw new IllegalArgumentException("模型不支持当前输入参数：" + modelId);
        }
        LLMInputValidator.validate(prompt.getMessages(), model.inputCapabilities(options));
        // 2026/11/04：直调与工具循环均检查完整输入，额度不足不改变 max-tokens。
        options.findContextWindow().ifPresent(context -> {
            // 2026/11/06：容量预检使用实际协议结构，避免重复计算 Schema 提示。
            var input = contextEstimator.estimate(new LLMPrompt(
                    model.getJsonOutputSupport(options.getModel()).messages(prompt.getMessages(), options),
                    checked, LLMOptionsPolicy.RESOLVED));
            context.requireFits(input.inputTokens(), options.getMaxTokens());
        });
        return options;
    }

    /**
     * 2026/11/06：预算、输入快照和实际调用共用已适配消息及原生输出选项。
     * @param prompt 原宿主输入
     * @return 固定的协议输入
     */
    @Override
    public ILLMPrompt prepareRequest(ILLMPrompt prompt) {
        var options = resolveOptions(prompt);
        var support = getModel(options.getProvider()).getJsonOutputSupport(options.getModel());
        return new LLMPrompt(support.messages(prompt.getMessages(), options),
                support.adapt(options, false), LLMOptionsPolicy.RESOLVED);
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
    private TaskFuture<ILLMResult> callInternal(LLMModelId modelId, ILLMPrompt prompt,
            @org.jspecify.annotations.Nullable ExecutionPolicy execution) {
        // 2026/11/03：只持锁至供应商同步构造完成，返回后的 HTTP 已持有自己的凭据快照。
        return fixed(modelId, () -> callFixed(modelId, prompt, execution));
    }

    /**
     * @param modelId 原路由
     * @param prompt 原已解析输入
     * @param execution 可选原策略
     * @return 实际模型阶段
     */
    private TaskFuture<ILLMResult> callFixed(LLMModelId modelId, ILLMPrompt prompt,
            @org.jspecify.annotations.Nullable ExecutionPolicy execution) {
        var model = getModel(modelId);
        var options = validateOptions(modelId, model, prompt);
        if (routes != null) {
            routes.source(modelId.id()).verify(options);
        } else if (fileEpoch != null) {
            fileEpoch.verify(options);
        }
        // 2026/11/06：实际请求仅发送声明支持的原生格式；不支持 JSON 时明确告警。
        var nativeOptions = options.findResponseFormat().orElse(LLMResponseFormat.TEXT) == LLMResponseFormat.JSON
                ? model.getJsonOutputSupport(options.getModel()).adapt(options, true) : options;
        var support = model.getJsonOutputSupport(options.getModel());
        // 2026/11/06：提前适配后的不支持模式仍在真实调用边界告警一次。
        if (support == org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonSupport.NONE
                && options.getResponseFormat() != LLMResponseFormat.JSON
                && prompt.getMessages().stream().anyMatch(
                        org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonInstruction.class::isInstance)) {
            var requested = LLMModelOptions.copyOf(options);
            requested.setResponseFormat(LLMResponseFormat.JSON);
            support.adapt(requested, true);
        }
        var effective = new LLMPrompt(support.messages(prompt.getMessages(), options), nativeOptions, LLMOptionsPolicy.RESOLVED);
        // 2026/11/02：等待供应商实际完成后再复核，不把禁用解释为已经撤销远端请求。
        var token = pluginToken(model);
        var result = execution == null ? model.call(effective) : model.call(effective, execution);
        return result.thenApply(value -> {
            if (pluginAccess != null) { pluginAccess.check(token); }
            if (execution != null) { execution.checkActive(); }
            return value;
        });
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
        // 2026/11/03：默认路由与同步请求构造互斥于文件发布。
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
    protected synchronized void _scan() {
        var candidates = new java.util.LinkedHashMap<LLMModelId, ILLMModel>();
        var modelPairs = beanScanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class);
        for (var pair : modelPairs.values()) {
            var annotation = pair.first();
            var model = pair.second();
            var modelId = parseModelId(annotation);
            var existing = candidates.putIfAbsent(modelId, model);
            if (existing != null) {
                throw new IllegalStateException("模型注册 ID 重复：" + modelId);
            }
        }
        if (pluginAccess != null) {
            candidates.values().forEach(model -> pluginAccess.register(model, MODEL_CALL));
        }
        modelMap = Map.copyOf(candidates);
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
     * 2026/11/05：保留在线快照，候选目录全部校验后由扫描阶段替换。
     */
    @Override
    protected void _clear() {
        // 2026/11/05：重扫只在候选完成后替换目录，不提前清空在线快照。
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
        var defaultOptions = defaults(modelId, baseOptions);
        var merged = LLMModelOptions.merge(defaultOptions, promptOptions);
        // 2026/11/06：仅固定路由，不能用默认 TEXT 的临时覆盖对象清掉请求 JSON 选项。
        merged.setProvider(modelId.id());
        return merged;
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

    /**
     * 2026/11/04：统一取得所属来源的模型初值，独立宿主继续支持原文件端口。
     * @param id 配置路由
     * @param base 协议基础参数
     * @return 不含秘密的有效初值
     */
    private LLMModelOptions defaults(LLMModelId id, ILLMModelOptions base) {
        return routes == null ? llmFileConfigService.getDefaultOptions(id, base)
                : routes.source(id.id()).defaults(id.id(), base);
    }
}
