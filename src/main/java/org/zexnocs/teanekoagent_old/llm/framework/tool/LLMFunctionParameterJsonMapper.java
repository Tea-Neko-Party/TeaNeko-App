package org.zexnocs.teanekoagent_old.llm.framework.tool;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMFunctionParameter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM Function Tool 参数到 JSON Schema 对象的公共转换器。
 * <br>供应商适配器应复用该类构造 function parameters，避免分别实现递归 schema 映射。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
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
