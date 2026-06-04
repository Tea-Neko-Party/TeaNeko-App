package org.zexnocs.teanekoagent.llm_api_framework.tool;

import lombok.*;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMToolCall;

/**
 * 大语言模型返回的工具调用请求。
 * <br>用于描述模型希望调用的 function 名称、调用 ID 和参数 JSON。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
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
     * <br>该名称应与工具注册表中的 {@link org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMTool#getName()} 一致。
     */
    private String name;

    /**
     * 模型生成的工具调用参数。
     * <br>通常为 JSON 字符串，由对应工具执行器解析和校验。
     */
    private String arguments;
}
