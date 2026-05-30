package org.zexnocs.teanekoagent.llm_api_framework.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMToolExecutor;

/**
 * 大语言模型 Function Tool 的通用实现。
 * <br>包含工具描述、参数 schema、严格模式和本地执行器。
 *
 * @author zExNocs
 * @date 2026/03/30
 * @since 4.4.0
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LLMTool implements ILLMTool {
    @Builder.Default
    private String type = "function";

    private String name;
    private String description;
    private ILLMFunctionParameter parameters;

    @Builder.Default
    private boolean strict = false;

    private String arguments;

    @JsonIgnore
    private ILLMToolExecutor executor;

    public static LLMTool function(String name,
                                   String description,
                                   ILLMFunctionParameter parameters,
                                   ILLMToolExecutor executor) {
        return LLMTool.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .executor(executor)
                .build();
    }

    @Override
    public String call() throws Exception {
        return call(arguments);
    }

    @Override
    public String call(String arguments) throws Exception {
        if (executor == null) {
            throw new UnsupportedOperationException("Tool executor is not configured: " + name);
        }
        return executor.call(arguments);
    }
}
