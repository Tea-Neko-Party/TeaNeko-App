package org.zexnocs.teanekoagent_old.llm.framework.tool;

import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent_old.llm.framework.tool.parameter.*;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Ignore;

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
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
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
            var parameterName = parameter.getName();
            properties.put(parameterName, fromParameter(parameter));
            if (isRequired(parameter)) {
                required.add(parameterName);
            }
        }
        return new LLMObjectFunctionParameter("Function tool parameters.", properties, required);
    }

    /**
     * 根据方法参数生成参数 Schema。
     *
     * @param parameter 方法参数
     * @return 参数 Schema
     */
    public static ILLMFunctionParameter fromParameter(Parameter parameter) {
        return fromType(parameter.getParameterizedType(), getDescription(parameter));
    }

    /**
     * 根据 Java 类型生成参数 Schema。
     *
     * @param type Java 类型
     * @param description 参数描述
     * @return 参数 Schema
     */
    public static ILLMFunctionParameter fromType(Type type, String description) {
        return fromType(type, description, new HashSet<>());
    }

    /**
     * 递归生成参数 Schema。
     *
     * @param type Java 类型
     * @param description 参数描述
     * @param visiting 当前递归链路中正在处理的类
     * @return 参数 Schema
     */
    private static ILLMFunctionParameter fromType(Type type, String description, Set<Class<?>> visiting) {
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
                                                               String description,
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
     * @param clazz Java Class
     * @param description 参数描述
     * @param visiting 当前递归链路中正在处理的类
     * @return 参数 Schema
     */
    private static ILLMFunctionParameter fromClass(Class<?> clazz, String description, Set<Class<?>> visiting) {
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
        if (Map.class.isAssignableFrom(clazz) || clazz == Object.class) {
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
    private static ILLMFunctionParameter enumParameter(Class<?> clazz, String description) {
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
                                                          String description,
                                                          Set<Class<?>> visiting) {
        visiting.add(clazz);
        var properties = new LinkedHashMap<String, ILLMFunctionParameter>();
        var required = new java.util.ArrayList<String>();
        for (var field : getSerializableFields(clazz)) {
            properties.put(field.getName(), fromType(field.getGenericType(), getDescription(field), visiting));
            if (isRequired(field)) {
                required.add(field.getName());
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
                        && !field.isAnnotationPresent(Ignore.class)) {
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
    private static ILLMFunctionParameter objectWithAdditionalProperties(String description) {
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
    private static String getDescription(AnnotatedElement element) {
        var description = element.getAnnotation(Description.class);
        return description == null ? null : description.value();
    }
}
