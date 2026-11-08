package org.zexnocs.teanekoagent.llm.framework.tool.interfaces;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.framework.function.IMethodCallable;

/**
 * 大语言模型 Function Tool 接口。
 * <br>用于向不同供应商暴露统一的工具定义和执行入口。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 * @version 4.9.0-l
 */
public interface ILLMTool extends IMethodCallable<String> {
    /**
     * 类型，一般都为 function
     *
     * @return {@link String }
     */
    default String getType() {
        return "function";
    }

    /**
     * tool 的名字
     *
     * @return {@link String }
     */
    String getName();

    /**
     * tool 的描述
     *
     * @return {@link String }
     */
    String getDescription();

    /**
     * 获取输入参数
     *
     * @return {@link ILLMFunctionParameter }
     */
    ILLMFunctionParameter getParameters();

    /**
     * 如果设置为 true，API 确保输出始终符合函数的 JSON schema 定义
     *
     * @return boolean
     */
    boolean isStrict();

    /**
     * 获取工具所属包。
     * <br>该字段用于按包筛选需要暴露给模型的工具，不参与供应商 Function Tool Schema。
     *
     * @return 工具所属包
     */
    default String getToolPackage() {
        return "";
    }

    /**
     * 大模型给出的参数值。只在大模型需求时才提供。
     *
     * @return {@link String }
     */
    default String getArguments() {
        return null;
    }

    /**
     * 调用工具执行逻辑，返回结果。参数为大模型给出的参数值。只在大模型需求时才提供。
     *
     * @param arguments 参数
     * @return {@link String }
     * @throws Exception 可能的异常
     */
    default String call(String arguments) throws Exception {
        return call();
    }
}
