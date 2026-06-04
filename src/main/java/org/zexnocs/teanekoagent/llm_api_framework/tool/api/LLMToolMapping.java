package org.zexnocs.teanekoagent.llm_api_framework.tool.api;

import org.zexnocs.teanekocore.framework.description.Description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 大语言模型工具方法注解。
 * <br>标注在工具提供者的方法上后，方法会被自动转换为 {@link org.zexnocs.teanekoagent.llm_api_framework.tool.LLMTool}。
 *
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LLMToolMapping {
    /**
     * 工具名称。
     * <br>未设置时会继续读取 {@link #name()}；两者都未设置时使用方法名。
     *
     * @return 工具名称
     */
    String value() default "";

    /**
     * 工具名称。
     * <br>用于不适合使用 {@link #value()} 的场景。
     *
     * @return 工具名称
     */
    String name() default "";

    /**
     * 工具描述。
     * <br>未设置时会读取方法上的 {@link Description} 注解；仍未设置时使用方法名。
     *
     * @return 工具描述
     */
    String description() default "";

    /**
     * 工具所属包。
     * <br>该字段用于调用方按包选择暴露给模型的工具；Java 关键字限制下不能命名为 {@code package}。
     *
     * @return 工具所属包
     */
    String toolPackage() default "";

    /**
     * 是否启用严格参数模式。
     *
     * @return 如果启用严格参数模式则返回 {@code true}
     */
    boolean strict() default false;
}
