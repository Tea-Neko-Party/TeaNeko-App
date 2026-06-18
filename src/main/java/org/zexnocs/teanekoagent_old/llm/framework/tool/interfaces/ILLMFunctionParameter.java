package org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 参数接口。
 * <br>用于描述可转换为 JSON Schema 的函数参数结构。
 *
 * @author zExNocs
 * @date 2026/03/27
 * @since 4.4.0
 */
public interface ILLMFunctionParameter {
    /**
     * 获取参数在 JSON Schema 中的基础类型。
     * <br>常见取值包括 {@code object}、{@code array}、{@code string}、{@code number}、{@code integer} 与 {@code boolean}。
     *
     * @return 参数类型
     */
    String getType();

    /**
     * 获取参数描述。
     * <br>该描述会暴露给大语言模型，用于帮助模型理解参数含义和填写方式。
     *
     * @return 参数描述
     */
    String getDescription();

    /**
     * 获取必填属性名称列表。
     * <br>通常仅在 {@code object} 类型参数中使用；无必填属性时返回空列表。
     *
     * @return 必填属性名称列表
     */
    List<String> getRequired();

    /**
     * 获取对象类型参数的属性定义。
     * <br>非 {@code object} 类型参数默认返回空映射。
     *
     * @return 属性名称到参数定义的映射
     */
    default Map<String, ILLMFunctionParameter> getProperties() {
        return Map.of();
    }

    /**
     * 获取数组类型参数的元素定义。
     * <br>非 {@code array} 类型参数默认返回 {@code null}。
     *
     * @return 数组元素定义
     */
    default ILLMFunctionParameter getItems() {
        return null;
    }

    /**
     * 获取枚举可选值。
     * <br>当参数未限制枚举值时返回空列表。
     *
     * @return 枚举可选值列表
     */
    default List<String> getEnumValues() {
        return List.of();
    }

    /**
     * 判断对象类型参数是否允许额外属性。
     * <br>默认不允许未在 {@link #getProperties()} 中声明的额外属性。
     *
     * @return 如果允许额外属性则返回 {@code true}
     */
    default boolean isAdditionalProperties() {
        return false;
    }
}
