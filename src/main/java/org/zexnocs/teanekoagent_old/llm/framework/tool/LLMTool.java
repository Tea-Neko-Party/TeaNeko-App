package org.zexnocs.teanekoagent_old.llm.framework.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolExecutor;

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
    /**
     * 工具类型。
     * <br>当前框架主要支持 Function Tool，因此默认值为 {@code function}。
     */
    @Builder.Default
    private String type = "function";

    /**
     * 工具名称。
     * <br>该名称会暴露给大语言模型，并用于 {@link LLMToolService} 执行工具调用时查找工具。
     */
    private String name;

    /**
     * 工具描述。
     * <br>该描述会暴露给大语言模型，用于帮助模型判断何时调用该工具。
     */
    private String description;

    /**
     * 工具参数定义。
     * <br>用于描述 Function Tool 的 JSON Schema 参数结构。
     */
    private ILLMFunctionParameter parameters;

    /**
     * 是否启用严格参数模式。
     * <br>启用后表示供应商 API 应尽量确保模型输出符合 {@link #parameters} 定义的 Schema。
     */
    @Builder.Default
    private boolean strict = false;

    /**
     * 工具所属包。
     * <br>该字段仅用于按包筛选暴露给模型的工具，序列化工具定义时会忽略。
     */
    @JsonIgnore
    @Builder.Default
    private String toolPackage = "";

    /**
     * 模型生成的工具调用参数。
     * <br>通常为 JSON 字符串，仅在需要把工具定义和一次具体调用绑定在同一对象中时使用。
     */
    private String arguments;

    /**
     * 本地工具执行器。
     * <br>序列化工具定义给模型时会忽略该字段，避免泄露本地执行逻辑。
     */
    @JsonIgnore
    private ILLMToolExecutor executor;

    /**
     * 创建 Function Tool。
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param parameters 工具参数定义
     * @param executor 本地工具执行器
     * @return Function Tool 实例
     */
    public static LLMTool function(String name,
                                   String description,
                                   ILLMFunctionParameter parameters,
                                   ILLMToolExecutor executor) {
        return function(name, description, parameters, "", executor);
    }

    /**
     * 创建指定工具包的 Function Tool。
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param parameters 工具参数定义
     * @param toolPackage 工具所属包
     * @param executor 本地工具执行器
     * @return Function Tool 实例
     */
    public static LLMTool function(String name,
                                   String description,
                                   ILLMFunctionParameter parameters,
                                   String toolPackage,
                                   ILLMToolExecutor executor) {
        return LLMTool.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .toolPackage(toolPackage)
                .executor(executor)
                .build();
    }

    /**
     * 使用当前对象保存的参数调用工具。
     *
     * @return 工具执行结果
     * @throws Exception 当工具执行失败时抛出
     */
    @Override
    public String call() throws Exception {
        return call(arguments);
    }

    /**
     * 使用指定参数调用工具。
     *
     * @param arguments 模型生成的工具调用参数，通常为 JSON 字符串
     * @return 工具执行结果
     * @throws UnsupportedOperationException 当工具未配置执行器时抛出
     * @throws Exception 当工具执行器执行失败时抛出
     */
    @Override
    public String call(String arguments) throws Exception {
        if (executor == null) {
            throw new UnsupportedOperationException("Tool executor is not configured: " + name);
        }
        return executor.call(arguments);
    }
}
