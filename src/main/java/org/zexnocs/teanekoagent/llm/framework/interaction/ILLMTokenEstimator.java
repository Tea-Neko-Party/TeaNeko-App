package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;

/**
 * 模型请求发起前的输入用量估算端口，可替换为具体模型的本地 tokenizer。
 * 不允许在此端口发起额外供应商请求或读取 Agent 业务上下文。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface ILLMTokenEstimator {
    /**
     * 为固定输入和工具定义估算输入成本，结果必须保留估算方法标识。
     * @param prompt 已冻结且不含凭据的输入
     * @return 非负输入 token 估算
     */
    LLMTokenEstimate estimate(ILLMPrompt prompt);
}
