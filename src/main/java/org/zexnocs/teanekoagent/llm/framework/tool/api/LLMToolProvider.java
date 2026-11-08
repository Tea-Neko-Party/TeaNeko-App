package org.zexnocs.teanekoagent.llm.framework.tool.api;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 大语言模型工具提供者注解。
 * <br>标注在 Spring Bean 类上后，{@link LLMToolService}
 * 会扫描该类中带有 {@link LLMToolMapping} 的方法并自动注册为 Function Tool。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
 * @version 4.9.0-l
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMToolProvider {
    /**
     * 工具默认所属包。
     * <br>方法级 {@link LLMToolMapping#toolPackage()} 未设置时会使用该值。
     *
     * @return 工具默认所属包
     */
    String toolPackage() default "";
}
