package org.zexnocs.teanekoagent.llm.file_config.form;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明可扩展的凭据输入方式，最终值仍经原文件解析和凭据边界验证。
 *
 * 修改记录：<br>
 * 2026/11/04：扩展标识改用独立 id，避免 Spring 将业务值作为 Bean 名。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMCredentialInput {
    /**
     * 2026/11/04：此属性仅用于扩展注册，不参与容器组件命名。
     * @return 稳定且唯一的输入方式 ID
     */
    String id();
}

