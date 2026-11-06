package org.zexnocs.teanekoagent.component.llm.framework.tool;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMFunctionParameter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM Function Tool 参数到 JSON Schema 对象的公共转换器。
 * <br>供应商适配器应复用该类构造 function parameters，避免分别实现递归 schema 映射。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 * @version 4.8.6-l
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LLMFunctionParameterJsonMapper {
    /**
     * 将 Function Tool 参数转换为 JSON Schema 风格对象。
     *
     * @param parameter LLM Function Tool 参数
     * @return JSON Schema 对象
     */
    public static Map<String, Object> toJsonSchema(ILLMFunctionParameter parameter) {
        var schema = new LinkedHashMap<String, Object>();
        if (parameter == null) {
            schema.put("type", "object");
            schema.put("properties", Map.of());
            return schema;
        }
        putIfNotBlank(schema, "type", parameter.getType());
        // 2026/11/06：工具参数映射保留可执行容量限制，不仅输出 description。
        schema.putAll(parameter.getLimits().schema());
        putIfNotBlank(schema, "description", parameter.getDescription());
        if (parameter.getProperties() != null && !parameter.getProperties().isEmpty()) {
            var properties = new LinkedHashMap<String, Object>();
            parameter.getProperties().forEach((name, property) ->
                    properties.put(name, toJsonSchema(property)));
            schema.put("properties", properties);
        }
        if (parameter.getRequired() != null && !parameter.getRequired().isEmpty()) {
            schema.put("required", parameter.getRequired());
        }
        if (parameter.getItems() != null) {
            schema.put("items", toJsonSchema(parameter.getItems()));
        }
        if (parameter.getEnumValues() != null && !parameter.getEnumValues().isEmpty()) {
            schema.put("enum", parameter.getEnumValues());
        }
        schema.put("additionalProperties", parameter.isAdditionalProperties());
        return schema;
    }

    /**
     * 向 map 写入非空白字符串。
     *
     * @param target 目标 map
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
