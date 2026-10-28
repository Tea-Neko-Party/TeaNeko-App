package org.zexnocs.teanekoagent.llm.framework.tool;

import org.springframework.aop.support.AopUtils;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolExecutor;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 基于反射的 Function Tool 执行器。
 * <br>用于把模型生成的 JSON 参数反序列化为方法参数，并调用对应 Spring Bean 方法。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
public class ReflectiveLLMToolExecutor implements ILLMToolExecutor, IContextualToolExecutor {
    /**
     * Jackson ObjectMapper，用于解析工具调用参数和序列化返回值。
     */
    private final ObjectMapper objectMapper;

    /**
     * 工具方法所在的 Spring Bean。
     */
    private final Object bean;

    /**
     * 可在当前 Bean 实例上调用的工具方法。
     */
    private final Method method;

    /**
     * 创建反射工具执行器。
     *
     * @param objectMapper 项目 JSON 解析与转换器
     * @param bean 工具方法所在的 Spring Bean
     * @param method 被注册为工具的方法
     */
    public ReflectiveLLMToolExecutor(ObjectMapper objectMapper, Object bean, Method method) {
        this.objectMapper = objectMapper;
        this.bean = bean;
        this.method = AopUtils.selectInvocableMethod(method, bean.getClass());
        this.method.setAccessible(true);
    }

    /**
     * 执行工具方法。
     *
     * @param arguments 模型生成的 JSON 参数对象
     * @return 工具执行结果；非字符串返回值会序列化为 JSON
     * @throws Exception 当参数解析或工具方法执行失败时抛出
     * @throws UnsupportedOperationException 当该入口或适配器尚不支持请求的执行方式时
     */
    @Override
    public String call(String arguments) throws Exception {
        if (CompletionStage.class.isAssignableFrom(method.getReturnType())
                || java.util.Arrays.stream(method.getParameterTypes()).anyMatch(t -> t == ToolInvocationContext.class)) {
            throw new UnsupportedOperationException("This tool requires asynchronous contextual execution.");
        }
        return serialize(invoke(arguments, null));
    }

    /**
     * 通过固定方法执行工具，等待返回阶段后再转换结果；可信上下文与模型业务参数分别传入。
     *
     * @param call 模型产生的工具调用，身份不从参数中取得
     * @param context 用于本次处理的 {@code context} 数据
     * @return 等待工具业务结束后形成文本结果的阶段，异步失败继续传播
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     */
    @Override
    public CompletionStage<String> call(ILLMToolCall call, ToolInvocationContext context) throws Exception {
        context.control().checkActive();
        var result = invoke(call.getArguments(), context);
        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            if (!(result instanceof CompletionStage<?> stage)) {
                throw new IllegalStateException("Asynchronous tool returned a null Stage.");
            }
            return stage.thenApply(value -> serializeActive(value, context));
        }
        if (result instanceof CompletionStage<?> stage) {
            return stage.thenApply(value -> serializeActive(value, context));
        }
        return CompletableFuture.completedFuture(serializeActive(result, context));
    }

    /**
     * 在转换结果前重新检查控制状态，取消后不再序列化迟到业务对象。
     *
     * @param result 已经形成的处理结果
     * @param context 用于本次处理的 {@code context} 数据
     * @return 按本方法契约生成的文本或标识
     */
    private String serializeActive(Object result, ToolInvocationContext context) {
        context.control().checkActive();
        return serialize(result);
    }

    /**
     * 将已完成的工具值转换为文本或 JSON；拒绝尚未展开的完成阶段，空业务值转为空字符串。
     *
     * @param result 已经形成的处理结果
     * @return 按本方法契约生成的文本或标识
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     */
    private String serialize(Object result) {
        if (result instanceof CompletionStage<?>) {
            throw new IllegalStateException("A tool must complete its Stage before serialization.");
        }
        if (result == null) {
            return "";
        }
        if (result instanceof String stringResult) {
            return stringResult;
        }
        return objectMapper.writeValueAsString(result);
    }

    /**
     * 调用工具方法并返回原始结果对象。
     *
     * @param arguments 模型生成的 JSON 参数对象
     * @param context 用于本次处理的 {@code context} 数据
     * @return 原始结果对象
     * @throws Exception 当参数解析或方法执行失败时抛出
     * @throws RuntimeException 当该操作的校验或下游调用失败时
     */
    private Object invoke(String arguments, ToolInvocationContext context) throws Exception {
        try {
            return method.invoke(bean, buildArguments(arguments, context));
        } catch (InvocationTargetException e) {
            var targetException = e.getTargetException();
            if (targetException instanceof Exception exception) {
                throw exception;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(targetException);
        }
    }

    /**
     * 根据工具方法签名构造实参数组。
     *
     * @param arguments 模型生成的 JSON 参数对象
     * @param context 用于本次处理的 {@code context} 数据
     * @return 方法实参数组
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private Object[] buildArguments(String arguments, ToolInvocationContext context) {
        var parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }
        var argumentMap = parseArgumentMap(arguments);
        var values = new Object[parameters.length];
        for (var i = 0; i < parameters.length; i++) {
            var parameter = parameters[i];
            if (parameter.getType() == ToolInvocationContext.class) {
                values[i] = java.util.Objects.requireNonNull(context, "Trusted context is required.");
                continue;
            }
            var parameterName = parameter.getName();
            if (!argumentMap.containsKey(parameterName) || argumentMap.get(parameterName) == null) {
                values[i] = defaultValue(parameter.getType());
            } else {
                values[i] = convertValue(argumentMap.get(parameterName), parameter);
            }
        }
        return values;
    }

    /**
     * 解析模型生成的工具参数 JSON。
     *
     * @param arguments JSON 参数对象
     * @return 参数名称到参数值的映射
     */
    private Map<String, Object> parseArgumentMap(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        var mapType = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
        return objectMapper.readValue(arguments, mapType);
    }

    /**
     * 将单个 JSON 字段值转换为方法参数类型。
     *
     * @param value JSON 字段值
     * @param parameter 方法参数
     * @return 转换后的参数值
     */
    private Object convertValue(Object value, Parameter parameter) {
        if (parameter.getType() == String.class && value instanceof String stringValue) {
            return stringValue;
        }
        var json = objectMapper.writeValueAsString(value);
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructType(parameter.getParameterizedType()));
    }

    /**
     * 获取 Java 基础类型的默认值。
     *
     * @param type 参数类型
     * @return 默认值；非基础类型返回 {@code null}
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
