package org.zexnocs.teanekoagent.llm.framework.model.interfaces;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要注册到 LLM 模型服务的模型 Bean。
 * <br>只有同时带有该注解并实现 {@link ILLMModel} 的 Spring Bean 才会被扫描，模型注册 ID 仅从 {@link #id()} 读取。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 * @version 4.9.0-l
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
