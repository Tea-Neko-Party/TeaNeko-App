package org.zexnocs.teanekoagent.llm.framework.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * 固定一次交互的 schema 与执行目标；模型可见定义不携带执行器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMToolBinding {
    private final ILLMTool definition;
    private final IContextualToolExecutor executor;

    /**
     * 递归冻结模型可见定义并捕获执行器引用，拒绝非法名称、非对象根参数和循环 schema。
     *
     * @param definition 向模型暴露的工具定义
     * @param executor 本地实际执行器
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public LLMToolBinding(ILLMTool definition, IContextualToolExecutor executor) {
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
     * 复用已有绑定的不可变定义，仅替换受控执行链，不重复解析 schema。
     * @param source 定义已经冻结的绑定
     * @param executor 带宿主守卫的执行链
     */
    private LLMToolBinding(LLMToolBinding source, IContextualToolExecutor executor) {
        this.definition = source.definition;
        this.executor = executor;
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
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
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
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
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
