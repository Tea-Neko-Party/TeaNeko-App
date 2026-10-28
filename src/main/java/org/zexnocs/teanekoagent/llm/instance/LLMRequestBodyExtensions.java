package org.zexnocs.teanekoagent.llm.instance;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoagent.llm.framework.model.LLMOptionBoundary;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 扩展字段不能重定义 DTO 已建模的字段，避免重复 JSON key 绕过 Options/内容校验。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMRequestBodyExtensions {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private LLMRequestBodyExtensions() {}

    private static final ClassValue<Set<String>> RESERVED = new ClassValue<>() {
        /**
         * 从实际请求字段与协议控制字段建立不可变保留名称集合，避免扩展字段绕过输入验证。
         *
         * @param type 运行时类型
         * @return 保持本方法顺序和筛选规则的结果集合
         */
        @Override
        protected Set<String> computeValue(@NonNull Class<?> type) {
            var names = new HashSet<String>();
            for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (var field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    names.add(field.getName());
                    var property = field.getAnnotation(JsonProperty.class);
                    if (property != null && !property.value().isEmpty()) {
                        names.add(property.value());
                    }
                }
            }
            // 服务端已有上下文/提示词也不能从 body.* 绕过当前显式输入。
            names.addAll(Set.of("messages", "input", "model", "tools", "tool_choice", "stream",
                    "instructions", "previous_response_id", "conversation", "prompt", "background",
                    "max_tokens", "max_output_tokens", "max_completion_tokens"));
            return Set.copyOf(names);
        }
    };

    /**
     * 仅提取不冲突的 body 扩展，拒绝认证字段和已由统一协议控制的请求字段。
     *
     * @param metadata 不允许包含认证信息的扩展元数据
     * @param requestType 供应商请求 DTO 类型，用于确定受控字段
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    public static Map<String, Object> fromMetadata(Map<String, Object> metadata, Class<?> requestType) {
        LLMOptionBoundary.validateMetadata(metadata);
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        metadata.forEach((key, value) -> {
            if (key != null && key.startsWith("body.")) {
                var name = key.substring(5);
                if (name.isBlank() || RESERVED.get(requestType).contains(name)) {
                    throw new IllegalArgumentException("LLM body extension conflicts with a modeled or controlled request field.");
                }
                if (shouldWrite(value)) {
                    result.put(name, value);
                }
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 省略空扩展值，同时保留 false 和零等有实际语义的供应商参数。
     *
     * @param value 待处理的值
     * @return 当前校验或原子操作是否满足本方法约定的条件
     */
    private static boolean shouldWrite(Object value) {
        return switch (value) {
            case null -> false;
            case String text -> !text.isBlank();
            case Collection<?> collection -> !collection.isEmpty();
            case Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };
    }
}
