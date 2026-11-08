package org.zexnocs.teanekoagent.llm.framework.tool.parameter;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 初始化时冻结的参数容量约束，供 Schema 和 Java 校验共同使用。
 * <br>2026/11/06：明确未声明和零上限的区别，不在请求中重新发现注解。
 *
 * @param minLength 最少字符数
 * @param maxLength 最多字符数
 * @param minItems 最少元素数
 * @param maxItems 最多元素数
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
public record LLMParameterLimits(int minLength, int maxLength, int minItems, int maxItems) {
    public static final LLMParameterLimits NONE = new LLMParameterLimits(-1, -1, -1, -1);

    /**
     * 拒绝反向边界和不支持的哨兵值。
     * @param minLength 字符下限
     * @param maxLength 字符上限
     * @param minItems 元素下限
     * @param maxItems 元素上限
     * @throws IllegalArgumentException 当上下限不合法时
     */
    public LLMParameterLimits {
        if (minLength < -1 || maxLength < -1 || minItems < -1 || maxItems < -1
                || maxLength >= 0 && minLength > maxLength || maxItems >= 0 && minItems > maxItems) {
            throw new IllegalArgumentException("JSON 参数容量上下限无效。");
        }
    }

    /**
     * 校验声明位置，字符串限制不能误用于数组或对象。
     * @param type JSON 基础类型
     * @throws IllegalArgumentException 当约束与类型不匹配时
     */
    public void validateType(String type) {
        if ((minLength >= 0 || maxLength >= 0) && !"string".equals(type)
                || (minItems >= 0 || maxItems >= 0) && !"array".equals(type)) {
            throw new IllegalArgumentException("JSON 容量约束与字段类型不匹配：" + type);
        }
    }

    /** @return 只包含实际声明的标准 Schema 关键字 */
    public Map<String, Integer> schema() {
        var result = new LinkedHashMap<String, Integer>();
        if (minLength >= 0) { result.put("minLength", minLength); }
        if (maxLength >= 0) { result.put("maxLength", maxLength); }
        if (minItems >= 0) { result.put("minItems", minItems); }
        if (maxItems >= 0) { result.put("maxItems", maxItems); }
        return Map.copyOf(result);
    }

    /**
     * 使用 Unicode 字符数，避免将一个补充平面字符重复计数。
     * @param value 已绑定字符串
     * @return 是否满足长度约束
     */
    public boolean accepts(String value) {
        int length = value.codePointCount(0, value.length());
        return (minLength < 0 || length >= minLength) && (maxLength < 0 || length <= maxLength);
    }

    /**
     * 验证数组容量，不修改或截断输入。
     * @param size 实际元素数
     * @return 是否满足容量约束
     */
    public boolean acceptsItems(int size) {
        return (minItems < 0 || size >= minItems) && (maxItems < 0 || size <= maxItems);
    }
}
