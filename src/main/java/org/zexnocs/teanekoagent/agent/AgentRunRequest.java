package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 宿主规范化的一次请求，不接受模型提供的身份、执行器或配置绑定。
 * @param requestId 宿主生成的请求标识，不自动提供跨请求幂等
 * @param actor 可信发起者、来源和目标
 * @param activity 本次业务活动类别
 * @param messages 初始消息；业务历史由后续会话层管理
 * @param conversation 宿主会话代次；显式独立轮次可为空，默认邮箱始终重新绑定
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity,
                              List<ILLMMessage> messages, @Nullable AgentConversationStamp conversation) {
    /**
     * 创建尚未绑定会话的宿主输入，交给默认邮箱时由邮箱决定真实代次。
     * @param requestId 宿主请求标识
     * @param actor 可信身份与固定目标
     * @param activity 宿主选择的活动
     * @param messages 初始统一消息
     * @throws IllegalArgumentException 当消息协议或透明状态无法安全冻结时
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity, List<ILLMMessage> messages) {
        this(requestId, actor, activity, messages, null);
    }

    /**
     * 深层冻结输入，交付后修改原消息不会影响本轮。
     * @param requestId 宿主请求标识
     * @param actor 可信身份与固定目标
     * @param activity 宿主选择的活动
     * @param messages 初始统一消息
     * @param conversation 可选可信会话代次，不进入 Prompt metadata
     * @throws IllegalArgumentException 当消息协议或透明状态无法安全冻结时
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentRunRequest {
        Objects.requireNonNull(requestId, "Agent 请求标识不能为空。");
        Objects.requireNonNull(actor, "Agent 请求身份不能为空。");
        Objects.requireNonNull(activity, "Agent 活动不能为空。");
        if (conversation != null && !conversation.key().scope().equals(actor.scope())) {
            throw new IllegalArgumentException("会话代次与请求作用域不一致。");
        }
        messages = LLMProtocolSnapshots.messages(messages);
    }
}
