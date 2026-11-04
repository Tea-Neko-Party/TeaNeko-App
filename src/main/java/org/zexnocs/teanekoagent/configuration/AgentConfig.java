package org.zexnocs.teanekoagent.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

/**
 * LLM Agent 相关 spring 配置
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 * @version 4.8.0-l
 */
@Configuration
@AutoConfigurationPackage(basePackages = "org.zexnocs.teanekoagent")
public class AgentConfig {
}
