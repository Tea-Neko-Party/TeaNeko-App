package org.zexnocs.teanekoagent.llm.framework.interaction;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;

import java.util.concurrent.CompletionStage;

/**
 * 在可信上下文和共享预算内完成有限模型/工具交互，不读取或提交 Agent 业务历史。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：增加实际完成契约，供持有异步输入资源的调用者使用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.1-l
 * @see LLMInteractionResult
 */
public interface ILLMInteractionService {
    /**
     * 2026/11/10：等待已启动资源实际结束后交付终态，供资源清理及显式备用请求使用。
     * @param prompt 固定输入
     * @param tools 已授权工具
     * @param context 原身份及总控制
     * @param settings 每次交互限制
     * @param guard 动态准入
     * @return 已排空交互的结果，逻辑超时仍保留原失败状态
     * @throws UnsupportedOperationException 当实现不能保证真实排空时
     */
    default CompletionStage<LLMInteractionResult> interactSettled(ILLMPrompt prompt, LLMToolSet tools,
            ToolInvocationContext context, LLMInteractionSettings settings, ILLMInteractionGuard guard) {
        throw new UnsupportedOperationException("交互端口尚不支持实际资源排空。");
    }

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
     * 使用显式宿主守卫执行交互；未适配的实现必须拒绝，不能静默忽略动态准入。
     * @param prompt 固定输入及参数策略
     * @param tools 固定的可信工具集
     * @param context 宿主身份及共享执行控制
     * @param settings 本轮交互设置
     * @param guard 每个模型阶段实际准入前执行的守卫
     * @return 完整交互结果，拒绝不会继续后续动作
     * @throws UnsupportedOperationException 当实现尚不支持显式守卫时
     */
    default CompletionStage<LLMInteractionResult> interact(ILLMPrompt prompt, LLMToolSet tools,
            ToolInvocationContext context, LLMInteractionSettings settings, ILLMInteractionGuard guard) {
        throw new UnsupportedOperationException("交互端口尚不支持宿主动态准入守卫。");
    }

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
