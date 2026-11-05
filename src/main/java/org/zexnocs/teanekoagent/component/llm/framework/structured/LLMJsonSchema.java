package org.zexnocs.teanekoagent.component.llm.framework.structured;

import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMFunctionParameterSchemaFactory;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.*;

/**
 * 不可变输出 Schema，同时用于提示、协议适配与本地校验，不能包含任意运行对象。
 * <br>2026/11/06：复用工具类型描述，所有字段显式出现，可空字段使用 null 分支。
 *
 * @param name 协议中的稳定结构名称
 * @param schema 完整不可变 JSON Schema
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public record LLMJsonSchema(String name, Map<String, Object> schema) {
    /**
     * 固定嵌套容器，避免异步调用期间被外部更换字段。
     * @param name 固定结构名
     * @param schema JSON 树
     * @throws IllegalArgumentException 当名称或根类型无效时
     */
    public LLMJsonSchema {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}") || !"object".equals(schema.get("type"))) {
            throw new IllegalArgumentException("JSON 输出必须使用具名对象结构。");
        }
        schema = freezeMap(schema, 0);
    }

    /**
     * 仅供单例初始化或显式重扫调用；不在每次请求中反射输出类型。
     * @param type 根类型
     * @return 完整 Schema
     * @throws IllegalArgumentException 当存在无界 Map、循环类型或未解析泛型时
     */
    public static LLMJsonSchema fromType(Class<?> type) {
        var name = type.getSimpleName().replaceAll("[^a-zA-Z0-9_-]", "_");
        name = name.substring(0, Math.min(48, name.length())) + "_" + StableDigest.sha256(type.getName()).substring(0, 12);
        return new LLMJsonSchema(name, describe(LLMFunctionParameterSchemaFactory.fromOutputType(type), 0));
    }

    /**
     * 将共享类型描述收紧为有限对象 Schema；不把不支持的类型伪装成空对象。
     * @param parameter 原类型声明
     * @param depth 嵌套层级
     * @return 不含运行对象的 Schema
     */
    private static Map<String, Object> describe(ILLMFunctionParameter parameter, int depth) {
        if (depth > 16 || parameter.isAdditionalProperties()) {
            throw new IllegalArgumentException("JSON 输出类型不能包含无界对象、未解析泛型或循环引用。");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("type", parameter.getType());
        if (parameter.getDescription() != null && !parameter.getDescription().isBlank()) {
            result.put("description", parameter.getDescription());
        }
        if ("object".equals(parameter.getType())) {
            var properties = new LinkedHashMap<String, Object>();
            var required = Set.copyOf(parameter.getRequired());
            parameter.getProperties().forEach((name, value) -> {
                var child = describe(value, depth + 1);
                properties.put(name, required.contains(name) ? child
                        : Map.of("anyOf", List.of(child, Map.of("type", "null"))));
            });
            result.put("properties", properties);
            result.put("required", List.copyOf(properties.keySet()));
            result.put("additionalProperties", false);
        } else if ("array".equals(parameter.getType())) {
            result.put("items", describe(java.util.Objects.requireNonNull(parameter.getItems()), depth + 1));
        } else if (!parameter.getEnumValues().isEmpty()) {
            result.put("enum", parameter.getEnumValues());
        }
        return result;
    }

    /**
     * 复制 JSON 对象，不使用未经检查的泛型转换。
     * @param source 原映射
     * @param depth 当前深度
     * @return 不可变映射
     */
    private static Map<String, Object> freezeMap(Map<?, ?> source, int depth) {
        if (depth > 48) {
            throw new IllegalArgumentException("JSON Schema 嵌套超出限制。");
        }
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("JSON Schema 键必须为字符串。");
            }
            result.put(name, freeze(value, depth + 1));
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * 复制受支持的 JSON 值，不保留外部可变引用。
     * @param value 原值
     * @param depth 当前深度
     * @return 固定值
     */
    private static Object freeze(Object value, int depth) {
        if (depth > 48) {
            throw new IllegalArgumentException("JSON Schema 嵌套超出限制。");
        }
        return switch (value) {
            case Map<?, ?> map -> freezeMap(map, depth);
            case List<?> list -> list.stream().map(item -> freeze(item, depth + 1)).toList();
            case String string -> string;
            case Boolean bool -> bool;
            case Integer integer -> integer;
            default -> throw new IllegalArgumentException("JSON Schema 包含不支持的值类型。");
        };
    }
}
