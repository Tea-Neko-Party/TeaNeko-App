package org.zexnocs.teanekoagent.component.llm.framework.tool;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.component.llm.framework.tool.parameter.*;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Ignore;
import org.zexnocs.teanekocore.framework.description.JsonConstraint;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * Function Tool 参数 Schema 生成器。
 * <br>用于根据 Java 方法签名、参数类型和 {@link Description} 注解生成统一的
 * {@link ILLMFunctionParameter} 结构。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：结构化输出复用类型描述，支持显式 JSON 字段名并拒绝重复声明。
 * <br>2026/11/06：初始化时编译声明的长度、数量和枚举约束，运行时复用完整合同。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.8.6-l
 */
public final class LLMFunctionParameterSchemaFactory {
    /**
     * 私有构造器，避免工具类被实例化。
     */
    private LLMFunctionParameterSchemaFactory() {
    }

    /**
     * 根据方法签名生成工具参数对象 Schema。
     *
     * @param method 工具方法
     * @return 方法参数对象 Schema
     */
    public static ILLMFunctionParameter fromMethod(Method method) {
        var properties = new LinkedHashMap<String, ILLMFunctionParameter>();
        var required = new java.util.ArrayList<String>();
        for (var parameter : method.getParameters()) {
            if (parameter.getType() == ToolInvocationContext.class) {
                continue; // 仅由宿主注入；模型不能产生此参数。
            }
            var parameterName = parameter.getName();
            properties.put(parameterName, fromParameter(parameter));
            if (isRequired(parameter)) {
                required.add(parameterName);
            }
        }
        return new LLMObjectFunctionParameter("工具调用参数。", properties, required);
    }

    /**
     * 根据方法参数生成参数 Schema。
     *
     * @param parameter 方法参数
     * @return 参数 Schema
     */
    public static ILLMFunctionParameter fromParameter(Parameter parameter) {
        // 2026/11/06：方法参数与输出字段共用可执行约束声明。
        return constrain(fromType(parameter.getParameterizedType(), getDescription(parameter)), parameter);
    }

    /**
     * 根据 Java 类型生成参数 Schema。
     *
     * @param type Java 类型
     * @param description 参数描述
     * @return 参数 Schema
     */
    public static ILLMFunctionParameter fromType(Type type, @Nullable String description) {
        return fromType(type, description, new HashSet<>());
    }

    /**
     * 初始化时为完整输出类型生成声明；高频调用须读取业务目录缓存。
     * @param type 固定输出类型
     * @return 包含类型及字段描述的原始结构
     */
    public static ILLMFunctionParameter fromOutputType(Class<?> type) {
        return fromType(type, getDescription(type));
    }

    /**
     * 递归生成参数 Schema。
     *
     * @param type Java 类型
     * @param description 参数描述
     * @param visiting 当前递归链路中正在处理的类
     * @return 参数 Schema
     */
    private static ILLMFunctionParameter fromType(Type type, @Nullable String description, Set<Class<?>> visiting) {
        if (type instanceof ParameterizedType parameterizedType) {
            return fromParameterizedType(parameterizedType, description, visiting);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return new LLMArrayFunctionParameter(description,
                    fromType(genericArrayType.getGenericComponentType(), null, visiting));
        }
        if (type instanceof Class<?> clazz) {
            return fromClass(clazz, description, visiting);
        }
        return objectWithAdditionalProperties(description);
    }

    /**
     * 根据参数化类型生成参数 Schema。
     *
     * @param type 参数化类型
     * @param description 参数描述
     * @param visiting 当前递归链路中正在处理的类
     * @return 参数 Schema
     */
    private static ILLMFunctionParameter fromParameterizedType(ParameterizedType type,
                                                               @Nullable String description,
                                                               Set<Class<?>> visiting) {
        var rawType = type.getRawType();
        if (!(rawType instanceof Class<?> rawClass)) {
            return objectWithAdditionalProperties(description);
        }
        if (Optional.class.isAssignableFrom(rawClass)) {
            return fromType(type.getActualTypeArguments()[0], description, visiting);
        }
        if (Collection.class.isAssignableFrom(rawClass)) {
            var itemType = type.getActualTypeArguments().length == 0
                    ? Object.class
                    : type.getActualTypeArguments()[0];
            return new LLMArrayFunctionParameter(description, fromType(itemType, null, visiting));
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            return objectWithAdditionalProperties(description);
        }
        return fromClass(rawClass, description, visiting);
    }

    /**
     * 根据普通 Class 生成参数 Schema。
     *
     * @param clazz 用于本次处理的 {@code clazz} 数据
     * @param description 参数描述
     * @param visiting 当前递归链路中正在处理的类
     * @return 参数 Schema
     */
    private static ILLMFunctionParameter fromClass(Class<?> clazz, @Nullable String description, Set<Class<?>> visiting) {
        if (clazz.isArray()) {
            return new LLMArrayFunctionParameter(description, fromClass(clazz.getComponentType(), null, visiting));
        }
        if (clazz == String.class
                || clazz == Character.class
                || clazz == char.class
                || CharSequence.class.isAssignableFrom(clazz)
                || TemporalAccessor.class.isAssignableFrom(clazz)
                || java.util.Date.class.isAssignableFrom(clazz)
                || UUID.class == clazz
                || URI.class == clazz
                || URL.class == clazz
                || Path.class.isAssignableFrom(clazz)) {
            return new LLMStringFunctionParameter(description);
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return new LLMBooleanFunctionParameter(description);
        }
        if (clazz == byte.class
                || clazz == short.class
                || clazz == int.class
                || clazz == long.class
                || clazz == Byte.class
                || clazz == Short.class
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == BigInteger.class) {
            return new LLMIntegerFunctionParameter(description);
        }
        if (clazz == float.class
                || clazz == double.class
                || clazz == Float.class
                || clazz == Double.class
                || clazz == BigDecimal.class) {
            return new LLMNumberFunctionParameter(description);
        }
        if (clazz.isEnum()) {
            return enumParameter(clazz, description);
        }
        // 2026/11/06：原始集合及未绑定泛型不伪装成空对象，输出合同会明确拒绝。
        if (Map.class.isAssignableFrom(clazz) || Collection.class.isAssignableFrom(clazz)
                || Optional.class == clazz || clazz == Object.class || clazz.getTypeParameters().length > 0) {
            return objectWithAdditionalProperties(description);
        }
        if (visiting.contains(clazz)) {
            return objectWithAdditionalProperties(description);
        }
        return objectFromFields(clazz, description, visiting);
    }

    /**
     * 根据枚举类型生成字符串枚举参数。
     *
     * @param clazz 枚举类型
     * @param description 参数描述
     * @return 字符串枚举参数
     */
    private static ILLMFunctionParameter enumParameter(Class<?> clazz, @Nullable String description) {
        var enumValues = new java.util.ArrayList<String>();
        for (var constant : clazz.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant) {
                enumValues.add(enumConstant.name());
            }
        }
        return new LLMStringFunctionParameter(description, enumValues);
    }

    /**
     * 根据对象字段生成 object 参数。
     *
     * @param clazz 对象类型
     * @param description 参数描述
     * @param visiting 当前递归链路中正在处理的类
     * @return object 参数
     */
    private static ILLMFunctionParameter objectFromFields(Class<?> clazz,
                                                          @Nullable String description,
                                                          Set<Class<?>> visiting) {
        visiting.add(clazz);
        var properties = new LinkedHashMap<String, ILLMFunctionParameter>();
        var required = new java.util.ArrayList<String>();
        for (var field : getSerializableFields(clazz)) {
            // 2026/11/06：声明和实际 JSON 绑定采用同一显式字段名，冲突不静默覆盖。
            var declared = field.getAnnotation(JsonProperty.class);
            var name = declared == null || declared.value().isEmpty() ? field.getName() : declared.value();
            // 2026/11/06：只在描述构建期间读取约束，字段关系仍由领域事务复核。
            if (properties.putIfAbsent(name, constrain(fromType(field.getGenericType(), getDescription(field), visiting), field)) != null) {
                throw new IllegalArgumentException("JSON 描述字段重复：" + clazz.getName() + "." + name);
            }
            if (isRequired(field)) {
                required.add(name);
            }
        }
        visiting.remove(clazz);
        return new LLMObjectFunctionParameter(description, properties, required);
    }

    /**
     * 获取对象中参与 Schema 生成的字段。
     *
     * @param clazz 对象类型
     * @return 字段列表
     */
    private static List<Field> getSerializableFields(Class<?> clazz) {
        var fields = new java.util.ArrayList<Field>();
        var current = clazz;
        while (current != null && current != Object.class) {
            for (var field : current.getDeclaredFields()) {
                var modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers)
                        && !Modifier.isTransient(modifiers)
                        && !field.isSynthetic()
                        && !field.isAnnotationPresent(Ignore.class)
                        && (!field.isAnnotationPresent(JsonIgnore.class) || !field.getAnnotation(JsonIgnore.class).value())) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 创建允许额外属性的 object 参数。
     *
     * @param description 参数描述
     * @return object 参数
     */
    private static ILLMFunctionParameter objectWithAdditionalProperties(@Nullable String description) {
        return AbstractLLMFunctionParameter.builder()
                .type("object")
                .description(description)
                .additionalProperties(true)
                .build();
    }

    /**
     * 判断方法参数是否应被标记为 required。
     *
     * @param parameter 方法参数
     * @return 如果参数必填则返回 {@code true}
     */
    private static boolean isRequired(Parameter parameter) {
        return !isOptionalType(parameter.getParameterizedType())
                && !hasNullableAnnotation(parameter.getAnnotations())
                && !hasNullableAnnotation(parameter.getAnnotatedType().getAnnotations());
    }

    /**
     * 判断字段是否应被标记为 required。
     *
     * @param field 字段
     * @return 如果字段必填则返回 {@code true}
     */
    private static boolean isRequired(Field field) {
        return !isOptionalType(field.getGenericType())
                && !hasNullableAnnotation(field.getAnnotations())
                && !hasNullableAnnotation(field.getAnnotatedType().getAnnotations());
    }

    /**
     * 判断类型是否是 Optional。
     *
     * @param type Java 类型
     * @return 如果是 Optional 类型则返回 {@code true}
     */
    private static boolean isOptionalType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getRawType() == Optional.class;
        }
        return type == Optional.class;
    }

    /**
     * 判断注解列表中是否存在 Nullable 注解。
     *
     * @param annotations 注解列表
     * @return 如果存在 Nullable 注解则返回 {@code true}
     */
    private static boolean hasNullableAnnotation(Annotation[] annotations) {
        for (var annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals("Nullable")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取元素上的描述文本。
     *
     * @param element 可携带注解的反射元素
     * @return 描述文本
     */
    private static @Nullable String getDescription(AnnotatedElement element) {
        var description = element.getAnnotation(Description.class);
        return description == null ? null : description.value();
    }

    /**
     * 2026/11/06：将显式约束附加到已有类型结构；不从 description 猜测容量或枚举。
     * @param source 类型描述
     * @param element 初始化时已发现的字段或参数
     * @return 收紧后的描述，无注解时返回原描述
     */
    private static ILLMFunctionParameter constrain(ILLMFunctionParameter source, AnnotatedElement element) {
        var annotation = element.getAnnotation(JsonConstraint.class);
        return annotation == null ? source : new ConstrainedLLMFunctionParameter(source,
                new LLMParameterLimits(annotation.minLength(), annotation.maxLength(), annotation.minItems(), annotation.maxItems()),
                List.of(annotation.enumValues()));
    }
}
