package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;

import java.util.concurrent.CompletionStage;

/**
 * 在可信上下文和共享预算内完成有限模型/工具交互，不读取或提交 Agent 业务历史。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 * @see LLMInteractionResult
 */
public interface ILLMInteractionService {
    /**
     * 开始自动交互，取消由宿主上下文的执行控制传播；显式设置优先于本地默认值。
     *
     * @param prompt 初始消息及模型选项，不得包含未配对的工具历史
     * @param tools 本轮已授权工具绑定快照
     * @param context 宿主建立的可信身份、总预算及父截止时间
     * @param settings 本轮覆盖设置，空值表示调用时读取默认值
     * @return 只读完成阶段，配置、协议、预算及执行失败以明确结果返回
     */
    CompletionStage<LLMInteractionResult> interact(ILLMPrompt prompt, LLMToolSet tools,
                                                  ToolInvocationContext context, LLMInteractionSettings settings);

    /**
     * 使用本地默认设置开始交互，不创建新的父总预算。
     *
     * @param prompt 初始消息及模型选项
     * @param tools 固定的已授权工具
     * @param context 宿主执行上下文
     * @return 完整交互结果的只读完成阶段
     */
    default CompletionStage<LLMInteractionResult> interact(ILLMPrompt prompt, LLMToolSet tools,
                                                          ToolInvocationContext context) {
        return interact(prompt, tools, context, null);
    }
}
