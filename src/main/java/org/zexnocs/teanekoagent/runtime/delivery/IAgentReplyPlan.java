package org.zexnocs.teanekoagent.runtime.delivery;

import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.UUID;

/**
 * 已编译交付计划的不可变公共投影，业务协议与内容由贡献插件拥有。
 * <br>2026/11/07：Agent 只消费请求绑定、原文摘要与正式答案，不依赖表达协议。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentReplyPlan {
    /** @return 编译时固定的真实请求标识 */
    UUID requestId();

    /** @return 原模型答案摘要，用于拒绝把计划配到其他答案 */
    String sourceDigest();

    /** @return 2026/11/08：是否存在实际待发送内容，不向宿主暴露业务步骤类型 */
    boolean hasOutput();

    /** @return 完整验证并冻结的正式答案 */
    ILLMAssistantMessage answer();
}
