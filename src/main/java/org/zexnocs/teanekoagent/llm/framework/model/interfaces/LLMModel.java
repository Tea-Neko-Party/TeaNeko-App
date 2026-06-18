package org.zexnocs.teanekoagent.llm.framework.model.interfaces;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要注册到 LLM 模型服务的模型 Bean。
 * <br>只有同时带有该注解并实现 {@link ILLMModel} 的 Spring Bean 才会被扫描，模型注册 ID 仅从 {@link #id()} 读取。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMModel {
    /**
     * 模型适配器的唯一注册 ID。
     * <br>该值用于 {@code model.yml}、Agent 默认模型配置和运行时模型路由。
     *
     * @return 模型适配器注册 ID
     */
    String id();
}
