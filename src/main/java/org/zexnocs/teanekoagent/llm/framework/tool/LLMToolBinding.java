package org.zexnocs.teanekoagent.llm.framework.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * 固定一次交互的 schema 与执行目标；模型可见定义不携带执行器。
 * <br>2026/11/02：增加保持原 schema 与可信调用对象的异步环绕入口。
 * <br>2026/11/02：增加不可变 client 白名单，所有环绕和守卫保留该限制。
 * <br>2026/11/02：提供显式结构化结果投影，工具及插件保持字符串接口，图像独立回填。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.6.0-l
 */
public final class LLMToolBinding {
    private final ILLMTool definition;
    private final IContextualToolExecutor executor;
    // 2026/11/02：限制属于宿主绑定，不进入模型参数或工具 schema。
    private final Set<String> clients;
    // 2026/11/02：投影由工具适配器显式安装，所有包装均保留，不按正文猜测图像。
    private final java.util.function.Function<String, LLMToolOutput> outputProjection;

    /**
     * 递归冻结模型可见定义并捕获执行器引用，拒绝非法名称、非对象根参数和循环 schema。
     *
     * @param definition 向模型暴露的工具定义
     * @param executor 本地实际执行器
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public LLMToolBinding(ILLMTool definition, IContextualToolExecutor executor) {
        this(definition, executor, Set.of());
    }

    /**
     * 2026/11/02：固定工具及可用客户端，空白名单表示允许所有客户端。
     * @param definition 工具定义
     * @param executor 本地执行器
     * @param clients 允许的客户端 ID，不接受空白 ID
     * @throws IllegalArgumentException 当定义或客户端标识不合法时
     * @throws NullPointerException 当必需参数为空时
     */
    public LLMToolBinding(ILLMTool definition, IContextualToolExecutor executor, Set<String> clients) {
        this.clients = validatedClients(clients);
        this.outputProjection = LLMToolOutput::new;
        Objects.requireNonNull(definition, "definition");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (!"function".equals(definition.getType())
                || definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("A named function tool is required.");
        }
        var parameters = definition.getParameters();
        if (parameters == null) {
            parameters = org.zexnocs.teanekoagent.llm.framework.tool.parameter
                    .AbstractLLMFunctionParameter.object(null, Map.of(), List.of());
        }
        var snapshot = snapshot(parameters, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        if (!"object".equals(snapshot.getType())) {
            throw new IllegalArgumentException("Function parameters must be an object.");
        }
        this.definition = new Definition(definition, snapshot);
    }

    /**
     * 提供仅用于模型选项的不可执行定义，不向模型暴露本地执行器。
     *
     * @return 本轮冻结且不能直接执行的工具定义
     */
    public ILLMTool definition() {
        return definition;
    }

    /**
     * 2026/11/02：查询不可变客户端约束，不使用模型提供的客户端字段。
     * @param clientId 宿主固定的客户端 ID
     * @return 未限制或客户端属于白名单时为真
     */
    public boolean supportsClient(String clientId) { return clients.isEmpty() || clients.contains(clientId); }

    /**
     * 2026/11/02：为工具自己的返回协议安装结构化投影，仍等待完整执行及插件守卫成功。
     * @param projection 将完整文字结果转换为文字和已验证图片的纯函数
     * @return 保留原 schema、客户端范围和执行目标的新绑定
     */
    public LLMToolBinding withOutputProjection(java.util.function.Function<String, LLMToolOutput> projection) {
        return new LLMToolBinding(this, executor, clients, Objects.requireNonNull(projection, "工具结果投影不能为空。"));
    }

    /**
     * 在受控工具集内解释完整返回值，先投影后执行文本裁剪。
     * @param output 原工具及环绕完成后的结果
     * @return 显式结构化结果
     */
    LLMToolOutput projectOutput(String output) { return Objects.requireNonNull(outputProjection.apply(output), "工具结果投影不能为空。"); }

    /**
     * 2026/11/02：为已有绑定追加客户端限制，环绕不能扩大已有白名单。
     * @param allowed 允许的客户端 ID，空集合仅在原绑定未受限时代表全部
     * @return 定义与执行目标不变的独立绑定
     * @throws IllegalArgumentException 当试图扩大已有允许范围或标识不合法时
     */
    public LLMToolBinding forClients(Set<String> allowed) {
        var fixed = validatedClients(allowed);
        if (!clients.isEmpty() && (fixed.isEmpty() || !clients.containsAll(fixed))) {
            throw new IllegalArgumentException("工具包装不能扩大允许的客户端范围。");
        }
        return new LLMToolBinding(this, executor, fixed);
    }

    /**
     * 2026/11/02：复制客户端声明并拒绝没有稳定标识的条目。
     * @param values 待固定的白名单
     * @return 不可变客户端集合
     * @throws IllegalArgumentException 当标识为空白或包含外围空格时
     */
    private static Set<String> validatedClients(Set<String> values) {
        var fixed = Set.copyOf(values);
        if (fixed.stream().anyMatch(value -> value.isBlank() || !value.equals(value.strip()))) {
            throw new IllegalArgumentException("工具客户端标识不能为空白或包含外围空格。");
        }
        return fixed;
    }

    /**
     * 2026/11/02：包围实际异步工具调用，禁止重复执行或更换已校验的调用及身份。
     * @param interceptor 完整异步环绕
     * @return 独立绑定，schema 保持冻结
     */
    public LLMToolBinding intercept(ToolInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "工具拦截器不能为空。");
        return new LLMToolBinding(this, (call, context) -> {
            context.control().checkActive();
            var actualCompletion = new CompletableFuture<String>();
            return context.control().guard(org.zexnocs.teanekocore.plugin.operation.OperationPipeline.around(call, false, (input, next) -> {
                try {
                    return interceptor.intercept(input, context, (actual, trusted) -> {
                        if (actual != call || trusted != context) { return CompletableFuture.failedStage(new IllegalStateException("工具下一节点不可替换可信输入。")); }
                        return next.execute(actual);
                    });
                } catch (Exception failure) { return CompletableFuture.failedStage(failure); }
            }, actual -> {
                try {
                    context.control().checkActive();
                    Objects.requireNonNull(executor.call(actual, context), "工具实际操作不能返回空阶段。").whenComplete((value, failure) -> {
                        if (failure == null) { actualCompletion.complete(value); } else { actualCompletion.completeExceptionally(failure); }
                    });
                } catch (Exception failure) { actualCompletion.completeExceptionally(failure); }
                return actualCompletion.minimalCompletionStage();
            }).thenCompose(value -> actualCompletion.thenApply(ignored -> value)));
        });
    }

    /**
     * 工具的类型化异步环绕；宿主参数校验与预算在此边界之外执行。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    @FunctionalInterface
    public interface ToolInterceptor {
        /**
         * 组合原工具和结果处理，不提前返回或保留下一节点作后台调用。
         * @param call 原模型调用
         * @param context 原可信上下文
         * @param next 原工具实际操作，至多调用一次
         * @return 完整工具阶段
         * @throws Exception 当实际执行失败时
         */
        CompletionStage<String> intercept(org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall call,
                ToolInvocationContext context, IContextualToolExecutor next) throws Exception;
    }

    // 不对外导出执行器；只能通过固定工具集进行有校验的执行。
    /**
     * 仅向同包受控工具集提供固定执行器，不重新从全局目录按名称查找。
     *
     * @return 本绑定捕获的实际执行器引用
     */
    IContextualToolExecutor executor() {
        return executor;
    }

    /**
     * 复用已冻结定义，在实际执行器前追加宿主检查，不公开原执行器。
     * @param guard 生命周期锁外执行的准入检查，不得阻塞等待同一交互
     * @return 定义不变、执行前重新检查的独立绑定
     * @throws NullPointerException 当守卫为空时
     */
    public LLMToolBinding guarded(BiConsumer<ILLMToolCall, ToolInvocationContext> guard) {
        Objects.requireNonNull(guard, "工具准入守卫不能为空。");
        return new LLMToolBinding(this, (call, context) -> {
            context.control().checkActive();
            guard.accept(call, context);
            context.control().checkActive();
            return executor.call(call, context);
        });
    }

    /**
     * 等待异步宿主检查后切回指定业务执行器，取消或超时后的迟到允许不得执行工具。
     * @param guard 生命周期锁外的异步准入检查
     * @param businessExecutor 借用的工具业务执行器
     * @return 使用原冻结定义和实际执行器的独立受控绑定
     * @throws NullPointerException 当守卫或执行器为空时
     */
    public LLMToolBinding guardedAsync(BiFunction<ILLMToolCall, ToolInvocationContext, CompletionStage<Void>> guard, Executor businessExecutor) {
        Objects.requireNonNull(guard, "异步工具准入守卫不能为空。"); Objects.requireNonNull(businessExecutor, "工具业务执行器不能为空。");
        return new LLMToolBinding(this, (call, context) -> {
            context.control().checkActive();
            return Objects.requireNonNull(guard.apply(call, context), "异步工具准入阶段不能为空。")
                    .thenComposeAsync(ignored -> {
                        try { context.control().checkActive(); return executor.call(call, context); }
                        catch (Exception failure) { return CompletableFuture.failedStage(failure); }
                    }, businessExecutor);
        });
    }

    /**
     * 复用已有绑定的不可变定义，仅替换受控执行链，不重复解析 schema。
     * @param source 定义已经冻结的绑定
     * @param executor 带宿主守卫的执行链
     */
    private LLMToolBinding(LLMToolBinding source, IContextualToolExecutor executor) {
        // 2026/11/02：同步守卫、异步守卫和插件环绕均保留原客户端限制。
        this(source, executor, source.clients);
    }

    /**
     * 2026/11/02：复用冻结定义与已校验的客户端范围，不重复解析 schema。
     * @param source 原始绑定
     * @param executor 包装后的执行链
     * @param clients 已固定的客户端范围
     */
    private LLMToolBinding(LLMToolBinding source, IContextualToolExecutor executor, Set<String> clients) {
        // 2026/11/02：客户端限制和环绕均不能意外丢弃结构化结果投影。
        this(source, executor, clients, source.outputProjection);
    }

    /**
     * 固定执行链及显式结果解释，复用已冻结 schema。
     * @param source 已冻结的原绑定
     * @param executor 完整执行链
     * @param clients 不可变客户端范围
     * @param outputProjection 已声明的结果投影
     */
    private LLMToolBinding(LLMToolBinding source, IContextualToolExecutor executor, Set<String> clients,
            java.util.function.Function<String, LLMToolOutput> outputProjection) {
        this.definition = source.definition;
        this.executor = executor;
        this.clients = clients;
        this.outputProjection = outputProjection;
    }

    /**
     * 获取当前执行器引用，后续修改 LLMTool Bean 不影响已创建绑定。
     *
     * @param tool 用于本次处理的 {@code tool} 数据
     * @return 固定可见 schema 与本地执行器引用的工具绑定
     * @throws NullPointerException 当必需的输入或依赖为空时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public static LLMToolBinding from(ILLMTool tool) {
        if (tool instanceof LLMTool value) {
            var contextual = value.getContextualExecutor();
            if (contextual != null) {
                return new LLMToolBinding(tool, contextual);
            }
            var synchronous = Objects.requireNonNull(value.getExecutor(), "Tool executor is missing.");
            return new LLMToolBinding(tool, IContextualToolExecutor.synchronous(synchronous));
        }
        if (tool instanceof IContextualToolExecutor contextual) {
            return new LLMToolBinding(tool, contextual);
        }
        return new LLMToolBinding(tool, IContextualToolExecutor.synchronous(tool::call));
    }

    /**
     * 递归复制参数 schema 的全部集合，检测深度、循环及无定义的必需字段。
     *
     * @param source 待处理的来源数据
     * @param visiting 当前递归路径中的对象身份集合
     * @param depth 当前嵌套深度
     * @return 递归冻结后的参数定义，不共享来源的可变集合
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private static ILLMFunctionParameter snapshot(
            ILLMFunctionParameter source, Set<ILLMFunctionParameter> visiting, int depth) {
        Objects.requireNonNull(source, "parameter");
        if (depth > 64 || !visiting.add(source)) {
            throw new IllegalArgumentException("Recursive or excessively deep tool schema.");
        }
        try {
            if (!Set.of("object", "array", "string", "integer", "number", "boolean").contains(source.getType())) {
                throw new IllegalArgumentException("Unsupported tool parameter type.");
            }
            var properties = new LinkedHashMap<String, ILLMFunctionParameter>();
            source.getProperties().forEach((name, value) -> properties.put(
                    Objects.requireNonNull(name, "property name"), snapshot(value, visiting, depth + 1)));
            var required = List.copyOf(source.getRequired());
            if (!properties.keySet().containsAll(required)) {
                throw new IllegalArgumentException("Required tool parameter is not defined.");
            }
            var items = source.getItems() == null ? null : snapshot(source.getItems(), visiting, depth + 1);
            return new Parameter(source.getType(), source.getDescription(), required,
                    Collections.unmodifiableMap(properties), items, List.copyOf(source.getEnumValues()),
                    source.isAdditionalProperties());
        } finally {
            visiting.remove(source);
        }
    }

    /**
     * 本轮模型可见的不可执行工具定义，与实际执行器引用分离。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    @Getter
    private static final class Definition implements ILLMTool {
        private final String type;
        private final String name;
        private final String description;
        private final ILLMFunctionParameter parameters;
        private final boolean strict;
        @JsonIgnore
        private final String toolPackage;

        /**
         * 复制工具可见元信息并绑定已冻结的参数定义，不保存任何执行器。
         *
         * @param source 待处理的来源数据
         * @param parameters 工具参数 schema 或已绑定的方法参数
         */
        private Definition(ILLMTool source, ILLMFunctionParameter parameters) {
            this.type = source.getType();
            this.name = source.getName();
            this.description = source.getDescription();
            this.parameters = parameters;
            this.strict = source.isStrict();
            this.toolPackage = source.getToolPackage();
        }

        /**
         * 拒绝将模型可见的定义直接当作执行器，调用必须经过可信绑定和工具集。
         *
         * @return 按本方法契约生成的文本或标识
         * @throws UnsupportedOperationException 当该入口或适配器尚不支持请求的执行方式时
         */
        @Override
        public String call() {
            throw new UnsupportedOperationException("A tool definition cannot execute.");
        }
    }

    /**
     * 递归冻结的参数 schema，防止工具重载改变已暴露的定义。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    @Getter
    private static final class Parameter implements ILLMFunctionParameter {
        private final String type;
        private final String description;
        private final List<String> required;
        private final Map<String, ILLMFunctionParameter> properties;
        private final ILLMFunctionParameter items;
        private final List<String> enumValues;
        private final boolean additionalProperties;

        /**
         * 固定参数类型及其不可变子 schema，供本轮输入验证与供应商映射共用。
         *
         * @param type 运行时类型
         * @param description 供调用者理解职责的说明
         * @param required 步骤执行所必需的组件键
         * @param properties 参数名称与子 schema 的映射
         * @param items 数组元素 schema
         * @param enumValues 允许的字符串取值
         * @param additionalProperties 是否允许 schema 未声明的对象属性
         */
        private Parameter(String type, String description, List<String> required,
                          Map<String, ILLMFunctionParameter> properties, ILLMFunctionParameter items,
                          List<String> enumValues, boolean additionalProperties) {
            this.type = type;
            this.description = description;
            this.required = required;
            this.properties = properties;
            this.items = items;
            this.enumValues = enumValues;
            this.additionalProperties = additionalProperties;
        }
    }
}
