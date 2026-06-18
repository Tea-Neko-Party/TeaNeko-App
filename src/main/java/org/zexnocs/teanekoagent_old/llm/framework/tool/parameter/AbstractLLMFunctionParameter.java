package org.zexnocs.teanekoagent_old.llm.framework.tool.parameter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMFunctionParameter;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 参数的基础实现。
 * <br>用于描述兼容 JSON Schema 的参数类型、属性、必填字段和枚举值。
 *
 * @author zExNocs
 * @date 2026/03/29
 * @since 4.4.0
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AbstractLLMFunctionParameter implements ILLMFunctionParameter {
    /**
     * 参数在 JSON Schema 中的基础类型。
     * <br>常见取值包括 {@code object}、{@code array}、{@code string}、{@code number}、{@code integer} 与 {@code boolean}。
     */
    private String type;

    /**
     * 参数描述。
     * <br>该描述会传递给大语言模型，用于说明参数含义、取值规则或填写方式。
     */
    private String description;

    /**
     * 对象参数中的必填属性名称列表。
     * <br>非 {@code object} 参数通常保持为空列表。
     */
    @Builder.Default
    private List<String> required = List.of();

    /**
     * 对象参数中的属性定义。
     * <br>键为属性名称，值为属性对应的参数 Schema。
     */
    @Builder.Default
    private Map<String, ILLMFunctionParameter> properties = Map.of();

    /**
     * 数组参数中的元素定义。
     * <br>仅 {@code array} 参数需要设置该字段。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ILLMFunctionParameter items;

    /**
     * 参数允许的枚举值列表。
     * <br>序列化时会映射为 JSON Schema 的 {@code enum} 字段。
     */
    @JsonProperty("enum")
    @Builder.Default
    private List<String> enumValues = List.of();

    /**
     * 对象参数是否允许未声明的额外属性。
     * <br>序列化时会映射为 JSON Schema 的 {@code additionalProperties} 字段。
     */
    @JsonProperty("additionalProperties")
    @Builder.Default
    private boolean additionalProperties = false;

    /**
     * 创建对象类型的 Function Tool 参数。
     *
     * @param description 对象参数描述
     * @param properties 对象属性定义
     * @param required 必填属性名称列表
     * @return 对象类型参数定义
     */
    public static AbstractLLMFunctionParameter object(String description,
                                                      Map<String, ILLMFunctionParameter> properties,
                                                      List<String> required) {
        return AbstractLLMFunctionParameter.builder()
                .type("object")
                .description(description)
                .properties(properties)
                .required(required)
                .build();
    }

    /**
     * 创建基础类型的 Function Tool 参数。
     *
     * @param type JSON Schema 基础类型
     * @param description 参数描述
     * @return 基础类型参数定义
     */
    public static AbstractLLMFunctionParameter primitive(String type, String description) {
        return AbstractLLMFunctionParameter.builder()
                .type(type)
                .description(description)
                .build();
    }
}
