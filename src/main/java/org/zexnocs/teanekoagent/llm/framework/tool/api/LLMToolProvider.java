package org.zexnocs.teanekoagent.llm.framework.tool.api;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 大语言模型工具提供者注解。
 * <br>标注在 Spring Bean 类上后，{@link org.zexnocs.teanekoagent.llm.framework.tool.LLMToolService}
 * 会扫描该类中带有 {@link LLMToolMapping} 的方法并自动注册为 Function Tool。
 *
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
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
