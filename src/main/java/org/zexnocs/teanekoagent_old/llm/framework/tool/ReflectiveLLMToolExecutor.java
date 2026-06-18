package org.zexnocs.teanekoagent_old.llm.framework.tool;

import org.springframework.aop.support.AopUtils;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolExecutor;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 基于反射的 Function Tool 执行器。
 * <br>用于把模型生成的 JSON 参数反序列化为方法参数，并调用对应 Spring Bean 方法。
 *
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
 */
public class ReflectiveLLMToolExecutor implements ILLMToolExecutor {
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
     * @param objectMapper Jackson ObjectMapper
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
     */
    @Override
    public String call(String arguments) throws Exception {
        var result = invoke(arguments);
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
     * @return 原始结果对象
     * @throws Exception 当参数解析或方法执行失败时抛出
     */
    private Object invoke(String arguments) throws Exception {
        try {
            return method.invoke(bean, buildArguments(arguments));
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
     * @return 方法实参数组
     */
    private Object[] buildArguments(String arguments) {
        var parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }
        var argumentMap = parseArgumentMap(arguments);
        var values = new Object[parameters.length];
        for (var i = 0; i < parameters.length; i++) {
            var parameter = parameters[i];
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
