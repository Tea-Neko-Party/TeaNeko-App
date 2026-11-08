package org.zexnocs.teanekoagent.llm.framework.tool;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.*;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

/**
 * 大语言模型返回的工具调用请求。
 * <br>用于描述模型希望调用的 function 名称、调用 ID 和参数 JSON。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 * @version 4.9.0-l
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMToolCall implements ILLMToolCall {
    /**
     * 工具调用 ID。
     * <br>用于在后续 tool message 中关联本次模型发起的工具调用。
     */
    private String id;

    /**
     * 工具调用类型。
     * <br>当前框架主要支持 Function Tool，因此默认值为 {@code function}。
     */
    @Builder.Default
    private String type = "function";

    /**
     * 被调用的工具名称。
     * <br>该名称应与工具注册表中的 {@link ILLMTool#getName()} 一致。
     */
    private String name;

    /**
     * 模型生成的工具调用参数。
     * <br>通常为 JSON 字符串，由对应工具执行器解析和校验。
     */
    private String arguments;
}
