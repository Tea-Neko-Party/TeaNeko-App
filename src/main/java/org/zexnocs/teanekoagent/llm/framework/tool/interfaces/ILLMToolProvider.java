package org.zexnocs.teanekoagent.llm.framework.tool.interfaces;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolMapping;

/**
 * 大语言模型工具提供者标记接口。
 * <br>实现该接口的 Spring Bean 中，带有
 * {@link LLMToolMapping} 的方法会被自动注册为工具。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
 * @version 4.9.0-l
 */
public interface ILLMToolProvider {
    /**
     * 获取该提供者下工具的默认所属包。
     * <br>方法级注解未声明包时会使用该值；默认空字符串表示不指定包。
     *
     * @return 工具默认所属包
     */
    default String getLLMToolPackage() {
        return "";
    }
}
