package org.zexnocs.teanekoagent.component.tool;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.NonNull;
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
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
public final class TeaNekoAgentToolMethods {
    /** 禁止实例化无状态编译入口。 */
    private TeaNekoAgentToolMethods() { }

    private static final ClassValue<List<MethodSpec>> METHODS = new ClassValue<>() {
        /**
         * 只编译类型及方法元数据，不取得业务 Bean。
         * @param type 真实声明类型
         * @return 稳定有序的方法规格
         */
        @Override protected List<MethodSpec> computeValue(@NonNull Class<?> type) { return compile(type); }
    };

    /**
     * 为显式宿主及自动注册共用同一注解编译器，缺少工具活动时不执行条件读取。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
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
        // 2026/11/04：工具及其业务组件必须都安装，不能靠默认工具贡献重新启用被卸下的能力。
        var declaration = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(bean), TeaNekoAgentToolClass.class);
        if (!definition.activities().contains(AgentActivity.TOOL) || !definition.assembly().has("tool")
                || declaration != null && Arrays.stream(declaration.requires()).anyMatch(id -> !definition.assembly().has(id))) {
            return List.of();
        }
        var context = new TeaNekoAgentToolContext(definition, request, runtime, input, null, null);
        var selected = new ArrayList<LLMToolBinding>();
        for (var spec : METHODS.get(AopUtils.getTargetClass(bean))) {
            if (!spec.clients().isEmpty() && !spec.clients().contains(request.actor().scope().clientId()) || !allowed(bean, spec, context)) { continue; }
            var binding = new LLMToolBinding(spec.tool(), (call, invocation) -> {
                var actual = new TeaNekoAgentToolContext(definition, request, runtime, input, call, invocation);
                return invocation.getControl().guard(CompletableFuture.supplyAsync(() -> {
                    actual.requireInvocation(); requireAllowed(bean, spec, actual);
                    try { return invoke(bean, spec.method(), arguments(spec.method(), call, actual, mapper)); }
                    catch (RuntimeException failure) { throw failure; }
                    catch (Exception failure) { throw new CompletionException(failure); }
                }, executor).thenCompose(value -> {
                    if (value instanceof CompletionStage<?> stage) { return stage.thenApply(result -> (Object) result); }
                    if (CompletionStage.class.isAssignableFrom(spec.method().getReturnType())) { throw new IllegalStateException("异步注解工具返回了空阶段。"); }
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
    public static List<String> names(Class<?> type) { return METHODS.get(type).stream().map(value -> value.tool().getName()).toList(); }

    /**
     * 2026/11/04：复用实际编译的工具元数据生成 API 目录，不实例化提供者或执行条件。
     * @param type 提供者真实类型
     * @return 参数定义及客户端范围，不含运行输入和秘密
     */
    public static List<Documentation> describe(Class<?> type) {
        return METHODS.get(type).stream().map(spec -> new Documentation(spec.tool().getName(),
                spec.tool().getDescription(), spec.clients(), spec.tool().getParameters(), spec.condition() != null)).toList();
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
     * @param type 真实提供者类型
     * @return 不可变方法规格
     * @throws IllegalArgumentException 当注解或方法合同不合法时
     */
    private static List<MethodSpec> compile(Class<?> type) {
        var owner = AnnotatedElementUtils.findMergedAnnotation(type, TeaNekoAgentToolClass.class);
        if (owner == null) { throw new IllegalArgumentException("工具类缺少 TeaNekoAgentToolClass 标记。"); }
        var methods = MethodIntrospector.selectMethods(type, (MethodIntrospector.MetadataLookup<TeaNekoAgentTool>) method ->
                AnnotatedElementUtils.findMergedAnnotation(method, TeaNekoAgentTool.class));
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
            result.add(new MethodSpec(method, condition, tool, clients, returnsMedia(method.getGenericReturnType())));
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
     * @param method 原签名
     * @param call 原模型调用
     * @param context 真实执行上下文
     * @param mapper 项目转换器
     * @return 与签名一致的实参
     */
    private static Object[] arguments(Method method, ILLMToolCall call, TeaNekoAgentToolContext context, ObjectMapper mapper) {
        var root = mapper.readTree(call.getArguments() == null || call.getArguments().isBlank() ? "{}" : call.getArguments());
        var parameters = method.getParameters(); var values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            var parameter = parameters[i]; var type = parameter.getType(); var annotation = parameter.getAnnotation(TeaNekoAgentToolParam.class);
            if (type == TeaNekoAgentToolContext.class) { values[i] = context; continue; }
            if (type == ToolInvocationContext.class) { values[i] = context.requireInvocation(); continue; }
            if (type == ILLMToolCall.class) { values[i] = call; continue; }
            var node = annotation != null && annotation.flatten() ? root : Objects.requireNonNull(root).get(parameterName(parameter));
            var javaType = mapper.getTypeFactory().constructType(parameter.getParameterizedType());
            if (node == null) {
                var fallback = parameter.getAnnotation(DefaultValue.class);
                values[i] = fallback == null ? type == Optional.class ? Optional.empty() : null : mapper.convertValue(fallback.value(), javaType);
            } else { values[i] = mapper.treeToValue(node, javaType); }
        }
        return values;
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
     * @param bean 实际 Spring 代理或独立对象
     * @param method 原方法
     * @param arguments 实参
     * @return 原始返回值
     * @throws Exception 当原业务调用失败时
     */
    private static Object invoke(Object bean, Method method, Object... arguments) throws Exception {
        try { var actual = AopUtils.selectInvocableMethod(method, bean.getClass()); ReflectionUtils.makeAccessible(actual); return actual.invoke(bean, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) { throw exception; }
            if (failure.getCause() instanceof Error error) { throw error; }
            throw new IllegalStateException("工具调用返回了无法分类的失败。");
        }
    }

    /**
     * 条件只读取宿主上下文，不从模型参数取得角色或开关。
     * @param bean 原提供者
     * @param spec 原方法规格
     * @param context 当前选择或执行阶段
     * @return 条件允许时为真
     */
    private static boolean allowed(Object bean, MethodSpec spec, TeaNekoAgentToolContext context) {
        if (spec.condition() == null) { return true; }
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
     *
     * @param method 原业务方法
     * @param condition 可选纯条件方法
     * @param tool 模型工具定义
     * @param clients 不可变客户端范围
     * @param media 是否声明结构化图像
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    private record MethodSpec(Method method, @Nullable Method condition, LLMTool tool, Set<String> clients, boolean media) { }
}
