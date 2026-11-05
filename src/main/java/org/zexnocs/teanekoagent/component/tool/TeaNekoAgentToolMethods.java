package org.zexnocs.teanekoagent.component.tool;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.zexnocs.teanekoagent.component.llm.framework.LLMOperationRejectedException;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.component.llm.framework.tool.*;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.component.llm.framework.tool.parameter.AbstractLLMFunctionParameter;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolParam;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.description.Description;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.*;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 注解方法编译及执行入口，复用既有参数 schema 和工具绑定，不直接访问平台或数据库。
 * 元数据按真实类型缓存；每轮仍固定原请求，业务方法通过实际 Spring Bean 代理调用。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按组件筛选工具并导出无秘密的实际参数说明。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * <br>2026/11/05：初始化时编译类依赖和代理方法，选择及调用只读目录并复核动态资格。
 * <br>2026/11/05：独立宿主按 Bean 弱身份绑定真实声明，隔离共享 JDK 代理类的不同提供者。
 * <br>2026/11/06：继承方法的调用候选编入各提供者规格，条件和业务方法不共享代理接口目录。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.3-l
 */
public final class TeaNekoAgentToolMethods {
    /** 禁止实例化无状态编译入口。 */
    private TeaNekoAgentToolMethods() { }

    // 2026/11/06：每个真实声明独立持有完整调用目录，运行期只读，未初始化不补扫描。
    private static final java.util.concurrent.ConcurrentMap<Class<?>, List<MethodSpec>> METHODS =
            new java.util.concurrent.ConcurrentHashMap<>();
    // 2026/11/05：代理 class 不是提供者身份；弱键不延长容器中 Bean 的生命周期。
    private static final ReferenceQueue<Object> RELEASED_PROVIDERS = new ReferenceQueue<>();
    private static final java.util.concurrent.ConcurrentMap<ProviderKey, Class<?>> PROVIDERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 初始化独立宿主的提供者；必须在构造、装配或低频显式重扫中调用。
     * 2026/11/05：完整编译后才发布身份绑定，清理已释放提供者的弱键。
     * @param bean 已创建的实际提供者或代理
     */
    public static void prepare(Object bean) {
        var type = AopUtils.getTargetClass(bean);
        prepare(type);
        for (var released = RELEASED_PROVIDERS.poll(); released != null; released = RELEASED_PROVIDERS.poll()) {
            PROVIDERS.remove(released);
        }
        PROVIDERS.put(new ProviderKey(bean, RELEASED_PROVIDERS), type);
    }

    /**
     * 初始化已发现声明，运行查询不会自行编译。
     * 2026/11/06：条件和业务调用候选随本类型规格完整编译后原子发布，失败不暴露半成品。
     * @param type 已知真实类型
     * @return 完整编译目录
     */
    private static List<MethodSpec> prepare(Class<?> type) {
        return METHODS.computeIfAbsent(type, TeaNekoAgentToolMethods::compile);
    }

    /**
     * 2026/11/06：只为当前提供者编译调用候选，共享父类 Method 不代表共享代理接口。
     * @param method 原业务或条件方法
     * @param interfaces 当前真实提供者公开的接口
     * @return 不可变候选，只由当前提供者的方法规格持有
     */
    private static List<Method> invocable(Method method, Class<?>[] interfaces) {
        var methods = new LinkedHashSet<Method>();
        methods.add(method);
        for (var contract : interfaces) {
            try {
                methods.add(contract.getMethod(method.getName(), method.getParameterTypes()));
            } catch (NoSuchMethodException absent) {
                // 接口不声明此签名时不构成调用候选，原方法仍可用于普通对象及类代理。
            }
        }
        methods.forEach(ReflectionUtils::makeAccessible);
        return List.copyOf(methods);
    }

    /**
     * 为显式宿主及自动注册共用同一注解编译器，缺少工具活动时不执行条件读取。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * 2026/11/05：只读本 Bean 的初始化绑定，不在查询时解包代理或发现注解。
     * @param bean 已注册或由独立宿主拥有的工具对象
     * @param definition 当前实例定义
     * @param request 固定请求
     * @param runtime 可选运行快照
     * @param input 可选实际布局
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @return 本轮符合 client 和条件的固定工具
     */
    public static List<LLMToolBinding> select(Object bean, AgentDefinition definition, AgentRunRequest request,
            @Nullable AgentRuntimeSnapshot runtime, @Nullable AgentPromptInput input, ObjectMapper mapper, Executor executor) {
        var type = PROVIDERS.get(new ProviderKey(bean, null));
        if (type == null) {
            throw new IllegalStateException("工具提供者尚未初始化，请在宿主装配时调用 prepare(bean)。");
        }
        return select(type, bean, definition, request, runtime, input, mapper, executor);
    }

    /**
     * 不调用代理 equals/hashCode 的弱身份键，声明缓存不持有业务实例。
     * <br>2026/11/05：为同类 JDK 代理建立独立初始化绑定。
     *
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private static final class ProviderKey extends WeakReference<Object> {
        private final int identity;

        /**
         * 绑定实例身份；查询键不登记回收队列。
         * @param bean 实际提供者
         * @param queue 仅发布键使用的回收队列
         */
        private ProviderKey(Object bean, @Nullable ReferenceQueue<Object> queue) {
            super(bean, queue);
            identity = System.identityHashCode(bean);
        }

        /** @return 稳定的实例身份哈希，不调用工具代理 */
        @Override
        public int hashCode() { return identity; }

        /**
         * 只比较仍存活的同一实例，已回收键只能与自身相等。
         * @param other 另一个键
         * @return 同一引用或相同存活实例时为真
         */
        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) { return true; }
            var bean = get();
            return bean != null && other instanceof ProviderKey key && bean == key.get();
        }
    }

    /**
     * 使用扫描器已发布的真实类型，不在每轮解析代理或类注解。
     * @param type 初始化或重扫时取得的声明类型
     * @param bean 原业务 Bean
     * @param definition 当前实例
     * @param request 固定请求
     * @param runtime 可选快照
     * @param input 实际布局
     * @param mapper 项目转换器
     * @param executor 借用执行器
     * @return 当前获准的工具
     * @throws IllegalStateException 当宿主尚未初始化该类型目录时
     */
    public static List<LLMToolBinding> select(Class<?> type, Object bean, AgentDefinition definition,
            AgentRunRequest request, @Nullable AgentRuntimeSnapshot runtime, @Nullable AgentPromptInput input,
            ObjectMapper mapper, Executor executor) {
        var specs = METHODS.get(type);
        if (specs == null) { throw new IllegalStateException("工具声明尚未初始化，请在宿主装配时编译目录。"); }
        if (!definition.activities().contains(AgentActivity.TOOL) || !definition.assembly().has("tool")) {
            return List.of();
        }
        var context = new TeaNekoAgentToolContext(definition, request, runtime, input, null, null);
        var selected = new ArrayList<LLMToolBinding>();
        // 2026/11/05：类级组件依赖随方法目录预编译，选择时不再发现或解析类注解。
        for (var spec : specs) {
            if (spec.requires().stream().anyMatch(id -> !definition.assembly().has(id))) { continue; }
            if (!spec.clients().isEmpty() && !spec.clients().contains(request.actor().scope().clientId()) || !allowed(bean, spec, context)) { continue; }
            var binding = new LLMToolBinding(spec.tool(), (call, invocation) -> {
                var actual = new TeaNekoAgentToolContext(definition, request, runtime, input, call, invocation);
                return invocation.getControl().guard(CompletableFuture.supplyAsync(() -> {
                    actual.requireInvocation(); requireAllowed(bean, spec, actual);
                    // 2026/11/06：调用只读当前提供者的候选，不能按共享父类 Method 查询其他提供者的接口。
                    try { return invoke(bean, spec.invocable(), arguments(spec, call, actual, mapper)); }
                    catch (RuntimeException failure) { throw failure; }
                    catch (Exception failure) { throw new CompletionException(failure); }
                }, executor).thenCompose(value -> {
                    if (value instanceof CompletionStage<?> stage) { return stage.thenApply(result -> (Object) result); }
                    if (spec.asynchronous()) { throw new IllegalStateException("异步注解工具返回了空阶段。"); }
                    return CompletableFuture.completedStage(value);
                }).thenApplyAsync(value -> {
                    actual.requireInvocation(); requireAllowed(bean, spec, actual);
                    return serialize(value, spec.media(), mapper);
                }, executor));
            }, spec.clients());
            selected.add(spec.media() ? binding.withOutputProjection(value -> media(value, mapper)) : binding);
        }
        return List.copyOf(selected);
    }

    /**
     * 在目录启动校验时读取导出名，不实例化任何业务服务。
     * @param type 提供者真实类型
     * @return 固定工具名集合
     */
    public static List<String> names(Class<?> type) { return prepare(type).stream().map(value -> value.tool().getName()).toList(); }

    /**
     * 2026/11/04：复用实际编译的工具元数据生成 API 目录，不实例化提供者或执行条件。
     * 2026/11/06：仅根据本规格的条件候选判断是否有动态准入，不读取其他提供者目录。
     * @param type 提供者真实类型
     * @return 参数定义及客户端范围，不含运行输入和秘密
     */
    public static List<Documentation> describe(Class<?> type) {
        return prepare(type).stream().map(spec -> new Documentation(spec.tool().getName(),
                spec.tool().getDescription(), spec.clients(), spec.tool().getParameters(), !spec.condition().isEmpty())).toList();
    }

    /**
     * 工具公开说明；条件存在仅说明还需运行时准入，不把目录当作执行授权。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param name 工具名
     * @param description 用途
     * @param clients 可用客户端，空表示所有
     * @param parameters 实际参数结构
     * @param conditional 是否有独立业务条件
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.8.0-l
     * @version 4.8.0-l
     */
    public record Documentation(String name, String description, Set<String> clients,
                                ILLMFunctionParameter parameters, boolean conditional) {}

    /**
     * 编译公开实例方法、参数 schema、client 和可选条件，错误在装配阶段明确拒绝。
     * 2026/11/06：代理接口候选仅在此初始化路径编译，并和方法规格一起发布。
     * @param type 真实提供者类型
     * @return 不可变方法规格
     * @throws IllegalArgumentException 当注解或方法合同不合法时
     */
    private static List<MethodSpec> compile(Class<?> type) {
        var owner = AnnotatedElementUtils.findMergedAnnotation(type, TeaNekoAgentToolClass.class);
        if (owner == null) { throw new IllegalArgumentException("工具类缺少 TeaNekoAgentToolClass 标记。"); }
        var methods = MethodIntrospector.selectMethods(type, (MethodIntrospector.MetadataLookup<TeaNekoAgentTool>) method ->
                AnnotatedElementUtils.findMergedAnnotation(method, TeaNekoAgentTool.class));
        var interfaces = org.springframework.util.ClassUtils.getAllInterfacesForClass(type);
        var result = new ArrayList<MethodSpec>(); var names = new HashSet<String>();
        for (var entry : methods.entrySet().stream().sorted(Comparator.comparing(value -> value.getKey().toGenericString())).toList()) {
            var method = entry.getKey(); var annotation = entry.getValue();
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) { throw new IllegalArgumentException("Agent 工具必须是公开实例方法：" + method.getName()); }
            var name = annotation.value().isBlank() ? method.getName() : annotation.value();
            if (!name.matches("[a-zA-Z0-9_-]{1,64}") || !names.add(name)) { throw new IllegalArgumentException("注解工具名称无效或重复：" + name); }
            Method condition = null;
            if (!annotation.condition().isBlank()) {
                try { condition = type.getMethod(annotation.condition(), TeaNekoAgentToolContext.class); }
                catch (NoSuchMethodException ignored) { throw new IllegalArgumentException("找不到公开的工具条件方法：" + annotation.condition()); }
                if (Modifier.isStatic(condition.getModifiers()) || condition.getReturnType() != boolean.class) { throw new IllegalArgumentException("工具条件必须是返回 boolean 的实例方法。"); }
            }
            var clients = Set.copyOf(Arrays.asList(owner.client())); var declared = Set.copyOf(Arrays.asList(annotation.client()));
            if (!clients.isEmpty() && !declared.isEmpty() && !clients.containsAll(declared)) { throw new IllegalArgumentException("工具方法不能扩大类声明的 client 范围。"); }
            if (!declared.isEmpty()) { clients = declared; }
            if (clients.stream().anyMatch(String::isBlank)) { throw new IllegalArgumentException("工具 client 标识不能为空白。"); }
            var description = AnnotatedElementUtils.findMergedAnnotation(method, Description.class);
            var tool = LLMTool.builder().name(name).description(description == null ? name : description.value()).toolPackage(owner.toolPackage())
                    .strict(annotation.strict()).parameters(schema(method)).build();
            result.add(new MethodSpec(invocable(method, interfaces),
                    condition == null ? List.of() : invocable(condition, interfaces),
                    tool, clients, returnsMedia(method.getGenericReturnType()),
                    Set.copyOf(Arrays.asList(owner.requires())),
                    Arrays.stream(method.getParameters()).map(TeaNekoAgentToolMethods::parameterSpec).toList(),
                    CompletionStage.class.isAssignableFrom(method.getReturnType())));
        }
        return List.copyOf(result);
    }

    /**
     * 复用单参数 schema 工厂，排除宿主注入项，展开 DTO 时禁止其他业务参数。
     * @param method 原方法
     * @return 模型业务参数 schema
     * @throws IllegalArgumentException 当名称、展开或可选基本类型声明不明确时
     */
    private static ILLMFunctionParameter schema(Method method) {
        var properties = new LinkedHashMap<String, ILLMFunctionParameter>(); var required = new ArrayList<String>();
        var business = Arrays.stream(method.getParameters()).filter(parameter -> !trusted(parameter.getType())).toList();
        for (var parameter : business) {
            var annotation = parameter.getAnnotation(TeaNekoAgentToolParam.class);
            var value = LLMFunctionParameterSchemaFactory.fromParameter(parameter);
            if (annotation != null && annotation.flatten()) {
                if (business.size() != 1 || !"object".equals(value.getType()) || value.isAdditionalProperties()) { throw new IllegalArgumentException("根 DTO 必须是唯一业务参数且具有明确字段。"); }
                properties.putAll(value.getProperties()); required.addAll(value.getRequired()); continue;
            }
            var name = parameterName(parameter);
            if (annotation != null && annotation.allowedValues().length > 0) {
                if (parameter.getType() != String.class) { throw new IllegalArgumentException("allowedValues 只用于字符串参数，其他枚举使用 Java 枚举类型。"); }
                value = AbstractLLMFunctionParameter.builder().type("string").description(value.getDescription()).enumValues(List.of(annotation.allowedValues())).build();
            }
            if (properties.putIfAbsent(name, value) != null) { throw new IllegalArgumentException("工具参数名重复：" + name); }
            boolean optional = parameter.isAnnotationPresent(DefaultValue.class) || parameter.getType() == Optional.class
                    || annotation != null && !annotation.required()
                    || java.util.stream.Stream.concat(Arrays.stream(parameter.getAnnotations()), Arrays.stream(parameter.getAnnotatedType().getAnnotations()))
                    .anyMatch(item -> item.annotationType().getSimpleName().equals("Nullable"));
            if (!optional) { required.add(name); }
            else if (parameter.getType().isPrimitive() && !parameter.isAnnotationPresent(DefaultValue.class)) {
                throw new IllegalArgumentException("可选基本类型工具参数必须提供 DefaultValue 或改用可空包装类型。");
            }
        }
        return AbstractLLMFunctionParameter.object("业务参数；身份、调用及执行控制由宿主注入，不接受模型声明。", properties, required);
    }

    /**
     * 解析已通过工具集校验的 JSON，可信对象直接注入，绝不从 JSON 反序列化。
     * 2026/11/05：参数名、默认值及展开规则从初始化目录读取，不在执行期重新发现注解。
     * @param spec 已编译签名
     * @param call 原模型调用
     * @param context 真实执行上下文
     * @param mapper 项目转换器
     * @return 与签名一致的实参
     */
    private static Object[] arguments(MethodSpec spec, ILLMToolCall call, TeaNekoAgentToolContext context, ObjectMapper mapper) {
        var root = mapper.readTree(call.getArguments() == null || call.getArguments().isBlank() ? "{}" : call.getArguments());
        var parameters = spec.parameters();
        var values = new Object[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            var type = parameter.type();
            if (type == TeaNekoAgentToolContext.class) { values[i] = context; continue; }
            if (type == ToolInvocationContext.class) { values[i] = context.requireInvocation(); continue; }
            if (type == ILLMToolCall.class) { values[i] = call; continue; }
            var node = parameter.flatten() ? root : Objects.requireNonNull(root).get(parameter.name());
            var javaType = mapper.getTypeFactory().constructType(parameter.genericType());
            if (node == null) {
                var fallback = parameter.defaultValue();
                values[i] = fallback == null ? type == Optional.class ? Optional.empty() : null : mapper.convertValue(fallback, javaType);
            } else { values[i] = mapper.treeToValue(node, javaType); }
        }
        return values;
    }

    /**
     * 2026/11/05：初始化时编译参数绑定规则，宿主注入项不要求模型参数名称。
     * @param parameter 真实签名参数
     * @return 不含当前请求或解析值的固定规则
     */
    private static ParameterSpec parameterSpec(Parameter parameter) {
        var annotation = parameter.getAnnotation(TeaNekoAgentToolParam.class);
        var fallback = parameter.getAnnotation(DefaultValue.class);
        var flatten = annotation != null && annotation.flatten();
        return new ParameterSpec(parameter.getType(), parameter.getParameterizedType(),
                trusted(parameter.getType()) || flatten ? "" : parameterName(parameter),
                flatten, fallback == null ? null : fallback.value());
    }

    /**
     * 按显式名称或真实参数名注册，缺少编译名称时要求开发者明确声明。
     * @param parameter 原参数
     * @return 模型参数名
     * @throws IllegalArgumentException 当名称缺失时
     */
    private static String parameterName(Parameter parameter) {
        var annotation = parameter.getAnnotation(TeaNekoAgentToolParam.class);
        if (annotation != null && !annotation.value().isBlank()) { return annotation.value(); }
        if (!parameter.isNamePresent()) { throw new IllegalArgumentException("工具参数需启用 -parameters 或使用 TeaNekoAgentToolParam 指定名称。"); }
        return parameter.getName();
    }

    /**
     * 显式识别仅宿主能够注入的参数类型。
     * @param type 参数类
     * @return 是否不进入模型 schema
     */
    private static boolean trusted(Class<?> type) { return type == TeaNekoAgentToolContext.class || type == ToolInvocationContext.class || type == ILLMToolCall.class; }

    /**
     * 通过代理可调用方法执行，不解包目标对象而绕过事务或其他 Spring 环绕。
     * 2026/11/06：直接读取当前提供者已编译的候选，不按 Method 共用接口目录。
     * @param bean 实际 Spring 代理或独立对象
     * @param methods 当前提供者的方法候选
     * @param arguments 实参
     * @return 原始返回值
     * @throws Exception 当原业务调用失败时
     */
    private static Object invoke(Object bean, List<Method> methods, Object... arguments) throws Exception {
        try {
            var actual = methods.stream()
                    .filter(candidate -> candidate.getDeclaringClass().isInstance(bean)).findFirst().orElse(null);
            if (actual == null) { throw new IllegalStateException("工具代理未公开已声明方法，请检查提供者接口。"); }
            return actual.invoke(bean, arguments);
        }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) { throw exception; }
            if (failure.getCause() instanceof Error error) { throw error; }
            throw new IllegalStateException("工具调用返回了无法分类的失败。");
        }
    }

    /**
     * 条件只读取宿主上下文，不从模型参数取得角色或开关。
     * 2026/11/06：条件使用所属提供者的接口候选，执行资格仍在每个边界实时复核。
     * @param bean 原提供者
     * @param spec 原方法规格
     * @param context 当前选择或执行阶段
     * @return 条件允许时为真
     */
    private static boolean allowed(Object bean, MethodSpec spec, TeaNekoAgentToolContext context) {
        if (spec.condition().isEmpty()) { return true; }
        try { return Boolean.TRUE.equals(invoke(bean, spec.condition(), context)); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("工具条件检查未完成。"); }
    }

    /**
     * 执行边界撤销时停止整个交互，不能继续后续工具或借用旧资格。
     * @param bean 原提供者
     * @param spec 固定方法
     * @param context 当前执行上下文
     * @throws LLMOperationRejectedException 当当前条件拒绝时
     */
    private static void requireAllowed(Object bean, MethodSpec spec, TeaNekoAgentToolContext context) {
        if (!spec.clients().isEmpty() && !spec.clients().contains(context.request().actor().scope().clientId()) || !allowed(bean, spec, context)) {
            throw new LLMOperationRejectedException();
        }
    }

    /**
     * 判断签名是否明确声明结构化视觉结果，普通 Object 返回不自动推测图片。
     * @param type 返回类型
     * @return 是否为直接或完成阶段包装的 LLMToolOutput
     */
    private static boolean returnsMedia(Type type) {
        if (type == LLMToolOutput.class) { return true; }
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw
                && CompletionStage.class.isAssignableFrom(raw) && parameterized.getActualTypeArguments()[0] == LLMToolOutput.class;
    }

    /**
     * 保持字符串结果不变，普通 DTO 转 JSON，显式图像只在内部编解码边界携带引用。
     * @param result 已完成业务结果
     * @param media 是否明确声明图像结果
     * @param mapper 项目转换器
     * @return 工具绑定的内部文字结果
     */
    private static String serialize(Object result, boolean media, ObjectMapper mapper) {
        if (result instanceof CompletionStage<?>) { throw new IllegalStateException("工具返回了尚未展开的嵌套阶段。"); }
        if (media) {
            if (!(result instanceof LLMToolOutput(String text, List<ImageLLMContentPart> images))) { throw new IllegalStateException("结构化图像工具未返回 LLMToolOutput。"); }
            return mapper.writeValueAsString(Map.of("text", text, "images", images.stream()
                    .map(image -> Map.of("url", image.getUrl(), "detail", image.getDetail().name())).toList()));
        }
        if (result instanceof LLMToolOutput) { throw new IllegalStateException("图像工具必须明确声明 LLMToolOutput 返回类型。"); }
        return result == null ? "" : result instanceof String text ? text : mapper.writeValueAsString(result);
    }

    /**
     * 解码显式类型产生的内部图像结果，继续由 LLMToolOutput 校验容量和引用。
     * @param text 内部已声明的图像结果
     * @param mapper 项目转换器
     * @return 与原 typed 结果一致的不可变负载
     */
    private static LLMToolOutput media(String text, ObjectMapper mapper) {
        var root = mapper.readTree(text); var images = new ArrayList<ImageLLMContentPart>();
        for (var image : root.path("images")) { images.add(new ImageLLMContentPart(image.path("url").stringValue(), LLMImageDetail.valueOf(image.path("detail").stringValue()))); }
        return new LLMToolOutput(root.path("text").stringValue(), images);
    }

    /**
     * 不可变方法元数据，不保存请求、Bean 或执行控制。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/06：业务与条件调用目录属于本提供者，不在全局按父类方法共享。
     *
     * @param invocable 当前提供者的业务调用候选
     * @param condition 当前提供者的纯条件调用候选，无条件时为空集合
     * @param tool 模型工具定义
     * @param clients 不可变客户端范围
     * @param requires 类级组件依赖，执行资格仍动态检查
     * @param media 是否声明结构化图像
     * @param parameters 已编译参数绑定规则
     * @param asynchronous 是否要求返回异步阶段
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.3-l
     */
    private record MethodSpec(List<Method> invocable, List<Method> condition, LLMTool tool, Set<String> clients, boolean media,
                              Set<String> requires, List<ParameterSpec> parameters, boolean asynchronous) { }

    /**
     * 参数绑定的静态声明，业务参数值及权限不缓存。
     * <br>2026/11/05：将注解读取移至工具初始化阶段。
     * @param type 声明类型
     * @param genericType 包含集合元素等信息的完整类型
     * @param name 模型字段名，可信或展开参数为空
     * @param flatten 是否将根对象绑定到唯一业务 DTO
     * @param defaultValue 省略字段时的声明默认值，无默认值为空
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record ParameterSpec(Class<?> type, Type genericType, String name, boolean flatten,
                                 @Nullable String defaultValue) { }
}
